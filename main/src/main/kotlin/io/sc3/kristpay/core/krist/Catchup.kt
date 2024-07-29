/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist

import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.krist.http.APITransaction
import io.sc3.kristpay.core.krist.http.KristHTTP
import io.sc3.kristpay.core.krist.ws.TransactionEvent
import io.sc3.kristpay.core.payment.Transactions
import io.sc3.kristpay.core.payment.Transactions.KRIST_TX_REF_KEY
import io.sc3.kristpay.core.payment.strategy.InflightKristTransactionFutures
import io.sc3.kristpay.model.*
import io.sc3.kristpay.util.startCoroutineTimer
import io.sc3.kristpay.util.supervised
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mu.KLoggable
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import io.sc3.kristpay.model.Transactions as DBTransactions

const val RESOLVED_TO_ADDRESS = "resolvedToAddress"

val CatchupScope = CoroutineScope(Dispatchers.IO.supervised())
object Catchup : KristPayConsumer(), KLoggable by KLogging() {
    private val config by CONFIG::krist

    private val lock = AtomicBoolean(false)
    private val repollSignal = AtomicBoolean(false)

    private val refundRatelimits = ConcurrentHashMap<KristAddress, Instant>()

    init {
        TransactionEvent.subscribe {
            if (lock.compareAndSet(false, true)) {
                try {
                    transaction(kpdb) {
                        processPolledTransaction(it)
                    }
                } finally {
                    lock.set(false)
                }
            } else {
                repollSignal.set(true)
                logger.warn("Transaction event fired while catchup was already running!")
            }
        }
    }

    fun startCatchupLoop() = CatchupScope.startCoroutineTimer(repeatDuration = 20.seconds) {
        do {
            if (lock.compareAndSet(false, true)) {
                try {
                    catchupTransactions()
                } finally {
                    lock.set(false)
                }
            }
        } while (repollSignal.compareAndSet(true, false))
    }

    fun cleanup() {
        logger.info("Cleaning up catchup...")
        CatchupScope.cancel()
    }

    // Repoll Krist in order to process any potentially missed WS transactions
    suspend fun catchupTransactions() {
        logger.debug { "Starting catchup..." }

        // First get the initial poll
        var latestTransactions = KristHTTP.lookup.transactions(
            address = config.address
        )

        val lastPolled = transaction(kpdb) {
            // First get the last transaction we polled to
            val lastPolledRow = LastPolledRow.get() ?: run {
                // Mark now as the genesis point, we don't want to grab
                // all txs in history for the particular address
                // But, if there are no transactions, we set as null
                // to mark that the next first transaction is the genesis point
                logger.info { "No last polled transaction found, setting genesis point with latest transaction: ${latestTransactions.transactions.firstOrNull()?.id}" }
                LastPolledRow.firstSet(latestTransactions.transactions.firstOrNull()?.id)
            }

            lastPolledRow.lastPolled
        }

        logger.info { "Catchup last polled transaction ID: $lastPolled" }

        val allTxs = mutableListOf<APITransaction>()
        allTxs.addAll(latestTransactions.transactions)

        var offset = allTxs.size
        while (latestTransactions.transactions.none { it.id == lastPolled }) {
            if (allTxs.size >= latestTransactions.total) {
                // If we've reached the end of the list, we're done
                break
            }

            logger.info { "Catchup was more than a page behind at id ${latestTransactions.transactions.last().id}; fetching next page..." }
            val limit = (if (lastPolled == null) {
                latestTransactions.total
            } else {
                latestTransactions.transactions.last().id - lastPolled
            }).coerceAtMost(1000)
            if (limit <= 0) {
                logger.warn { "Catchup paged past transaction id, txn must be missing... taking closest transaction" }
                break
            }

            latestTransactions = KristHTTP.lookup.transactions(
                address = CONFIG.krist.address,
                limit = limit,
                offset = offset
            )

            offset += latestTransactions.count
            allTxs.addAll(latestTransactions.transactions)
        }

        val transactionsToProcess = allTxs
            .distinctBy { it.id }              // Just in case a transaction came in while we queried, de-dupe the list
            .takeWhile { it.id != lastPolled } // Drop everything after and including the last polled transaction

        if (transactionsToProcess.isEmpty()) {
            logger.info { "Catchup found no transactions to process." }
            return
        }

        transaction(kpdb) {
            val lpRow = LastPolledRow.get()!!
            if (lpRow.lastPolled != lastPolled) { // Racing!!!
                logger.warn { "Last polled transaction changed while processing catchup, aborting. This is indicative of a race condition!!!" }
                return@transaction
            }
            lpRow.lastPolled = transactionsToProcess.first().id
            logger.info { "Last polled transaction set to ${lpRow.lastPolled}" }

            transactionsToProcess.forEach(::processPolledTransaction)
        }
    }

    private fun processPolledTransaction(tx: APITransaction) {
        logger.info { "Processing polled transaction with external reference ${tx.id}" }

        val isIncoming = tx.to == config.address
        val isOutgoing = tx.from == config.address

        if (!isIncoming && !isOutgoing) {
            // This shouldn't happen!!!
            return logger.warn("Transaction ${tx.id} is neither from or to ${config.address}")
        }

        if (isIncoming) processIncomingTransaction(tx)
        if (isOutgoing) processOutgoingTransaction(tx)
    }

    private fun processIncomingTransaction(tx: APITransaction) {
        logger.info { "Processing incoming tx: $tx" }
        val meta = tx.metadata?.let { CommonMeta(it) }

        val existingEntity = Transaction.find {
            (DBTransactions.externalReference eq tx.id) and (DBTransactions.from eq KristAddress(tx.from).serialize())
        }
        if (existingEntity.count() > 0) return // Already processed

        val referenceName = tx.sent_metaname
        val wallet = referenceName?.let {
            // By username
            Wallet.find { Wallets.name eq referenceName }.firstOrNull() ?:

            // By UUID
            runCatching { formatUUID(referenceName)?.let { UUID.fromString(it) } }.getOrNull()?.let {
                User.findById(it)?.defaultWallet
            }
        }

        val returnRecipient = meta?.returnRecipient?.takeIf { validateDestination(it) } ?: tx.from

        if (wallet != null) {
            API.initializeTransaction(
                from = KristAddress(tx.from),
                to = KristPayWallet(wallet.id.value),
                amount = MonetaryAmount(tx.value),
                metadata = tx.metadata,
                initiator = Initiator.KristAddress(returnRecipient),
                sendNotification = true,
                externalReference = tx.id
            )
        } else {
            // Create a dummy internal transaction that we can use to verify that we processed the transaction already.
            API.initializeTransaction(
                from = KristAddress(tx.from),
                to = KristPayUnallocated,
                amount = MonetaryAmount(tx.value),
                metadata = tx.metadata,
                initiator = Initiator.KristAddress(returnRecipient),
                sendNotification = false,
                externalReference = tx.id
            )

            // Don't refund if it is a donation or if the return recipient is invalid
            if (meta?.donate == true || !validateDestination(meta?.returnRecipient ?: tx.from)) return

            // Protect against refund loops
            var shouldRefund = true
            val kristRecipient = KristAddress(returnRecipient)
            refundRatelimits.compute(kristRecipient) { _, lastRefund ->
                val now = Clock.System.now()
                if (lastRefund?.let { now - it < config.refundRatelimit.seconds } == true) {
                    shouldRefund = false
                }

                now // Update the last refund time
            }

            if (shouldRefund) {
                // Refund the transaction, we don't know who it was for
                API.initializeTransaction(
                    from = KristPayUnallocated,
                    to = kristRecipient,
                    amount = MonetaryAmount(tx.value),
                    metadata = CommonMeta(
                        "error" to "Could not find user (send to `username@${config.advertisedName}.kst` to deposit, or set `donate=true` to donate)",
                        "return" to "false"
                    ).toString(),
                    initiator = Initiator.Server,
                    sendNotification = false
                )
            }
        }
    }

    // Takes a UUID without dashes and returns a UUID with dashes
    private fun formatUUID(referenceName: String): String? {
        if (referenceName.length != 32) return null

        return listOf(
            referenceName.substring(0, 8),
            referenceName.substring(8, 12),
            referenceName.substring(12, 16),
            referenceName.substring(16, 20),
            referenceName.substring(20, 32)
        ).joinToString("-")
    }

    private fun processOutgoingTransaction(tx: APITransaction) {
        logger.info { "Processing outgoing tx: $tx" }
        val meta = tx.metadata?.let { CommonMeta(it) }
            ?: return logger.warn("Outgoing Transaction ${tx.id} has no/invalid CommonMeta; cannot process... Was this a manual transaction?")

        val reference = meta.get(KRIST_TX_REF_KEY)?.runCatching { UUID.fromString(this) }?.getOrNull()
            ?: return logger.warn("Outgoing Transaction ${tx.id}'s reference was invalid! Was this a manual transaction?")

        val existingEntity = Transaction.findById(reference)
            ?: return logger.error("Outgoing Transaction ${tx.id} had reference of $reference, but did not exist!")

        // TODO: This seems unsafe
        if (tx.to != (existingEntity.to as KristAddress).address) {
            val new = (existingEntity.systemMetadata ?: mapOf()).plus(RESOLVED_TO_ADDRESS to JsonPrimitive(tx.to))
            existingEntity.systemMetadata = JsonObject(new)
        }

        // First check if it's an in-flight transaction
        val future = InflightKristTransactionFutures[reference]
        if (future != null) {
            future.complete(tx.id)
            return
        }

        // As a sanity check, verify that the amounts match
        if (existingEntity.amount != tx.value) {
            return logger.error("WEE WOO WEE WOO!!!! Outgoing Transaction ${tx.id}'s amounts did not match known values!")
        }

        existingEntity.externalReference = tx.id
        Transactions.restartPendingTransaction(existingEntity.toSnapshot())
    }
}
