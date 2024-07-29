/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.payment.strategy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KLogging
import io.sc3.kristpay.api.model.KristAddress
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.PaymentResult
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.krist.http.KristError
import io.sc3.kristpay.core.krist.http.KristHTTP
import io.sc3.kristpay.core.krist.http.KristHTTPException
import io.sc3.kristpay.core.krist.http.TransactionsController
import io.sc3.kristpay.core.payment.PaymentStrategy
import io.sc3.kristpay.core.payment.Transactions
import io.sc3.kristpay.model.Transaction
import io.sc3.kristpay.util.supervised
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

val InflightKristTransactionFutures = ConcurrentHashMap<UUID, CompletableFuture<Int>>()

val KristPaymentScope = CoroutineScope(Dispatchers.IO.supervised())
class KristPaymentStrategy(private val actor: KristAddress): PaymentStrategy {
    override suspend fun debit(amount: MonetaryAmount, pendingTransaction: Transaction, allowDebt: Boolean): PaymentResult {
        // Krist debits are always initiated based on a confirmed transaction,
        // so we don't need to do anything here.
        return PaymentResult.Success
    }

    override suspend fun checkPendingDebit(pendingTransaction: Transaction): PaymentResult {
        throw UnsupportedOperationException("KristPaymentStrategy does not support pending debits")
    }

    override suspend fun credit(amount: MonetaryAmount, pendingTransaction: Transaction): PaymentResult {
        logger.debug { "Setting future for ${pendingTransaction.id.value}" }
        val future = InflightKristTransactionFutures.computeIfAbsent(pendingTransaction.id.value) { CompletableFuture() }

        val attempt = pendingTransaction.attempt ?: 0
        KristPaymentScope.launch {
            if (attempt > 1) {
                val delayAmount = 5.seconds * minOf(3, attempt - 1)
                logger.info("Waiting for $delayAmount before retrying credit for ${pendingTransaction.id.value}")
                delay(delayAmount)
            }

            try {
                val res = KristHTTP.transactions.makeTransaction(
                    TransactionsController.MakeTransactionRequest(
                        privatekey = CONFIG.krist.privateKey,
                        to = actor.address,
                        amount = amount.amount,
                        metadata = pendingTransaction.metadata + ";${Transactions.KRIST_TX_REF_KEY}=${pendingTransaction.id.value}",
                        requestId = pendingTransaction.id.value.toString()
                    ),
                )

                future.complete(res.transaction.id)
            } catch (e: Exception) {
                logger.error("Krist transaction failed", e)
                future.completeExceptionally(e)
            }
        }

        return PaymentResult.Pending(future.handle { _, e ->
            if (e != null) {
                mapFailure(e)
            } else {
                PaymentResult.Success
            }
        })
    }

    private fun mapFailure(e: Throwable) = when ((e as? KristHTTPException)?.error?.code) {
        KristError.NameNotFound -> PaymentResult.InvalidName
        KristError.InsufficientFunds -> PaymentResult.InsufficientFunds

        KristError.TransactionConflict,
        KristError.InvalidParameter,
        KristError.MissingParameter,
        KristError.TransactionsDisabled -> PaymentResult.Rejected

        else -> PaymentResult.Error(e.message ?: "Unknown error")
    }

    override suspend fun checkPendingCredit(pendingTransaction: Transaction): PaymentResult {
        val future = InflightKristTransactionFutures[pendingTransaction.id.value]
        if (future == null) {
            // Credit is idempotent, so we can just re-run it
            logger.debug { "Idempotent retry of credit for ${pendingTransaction.id.value}" }
            return credit(MonetaryAmount(pendingTransaction.amount), pendingTransaction)
        }

        return if (future.isDone) {
            try {
                val externalReference = future.getNow(null) ?: throw RuntimeException()
                pendingTransaction.externalReference = externalReference

                PaymentResult.Success
            } catch (e: Exception) {
                when (e) {
                    is CompletionException -> mapFailure(e.cause ?: RuntimeException("Unknown error"))
                    is CancellationException -> PaymentResult.AttemptTimedOut
                    else -> {
                        logger.error("Error checking pending credit", e)
                        PaymentResult.Error(e.message ?: "Unknown error")
                    }
                }
            } finally {
                InflightKristTransactionFutures.remove(pendingTransaction.id.value)
            }
        } else {
            if (pendingTransaction.externalReference != null) {
                return try {
                    KristHTTP.transactions.getTransaction(pendingTransaction.externalReference!!)
                    PaymentResult.Success
                } catch (e: KristHTTPException) {
                    PaymentResult.Pending(future.handle { _, _ -> PaymentResult.Success })
                }
            }

            logger.warn { "Attempt timed out" }
            PaymentResult.AttemptTimedOut
        }
    }

    companion object : KLogging()
}
