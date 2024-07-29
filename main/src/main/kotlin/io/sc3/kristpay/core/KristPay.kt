/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core

import io.prometheus.client.Counter
import io.prometheus.client.exporter.HTTPServer
import io.sc3.kristpay.api.*
import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.api.model.frontend.TransactionSymbol
import io.sc3.kristpay.api.model.util.lazyMap
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.db.ConnectionManager
import io.sc3.kristpay.core.db.selectBuffered
import io.sc3.kristpay.core.krist.Catchup
import io.sc3.kristpay.core.krist.CommonMeta
import io.sc3.kristpay.core.krist.MasterWalletImpl
import io.sc3.kristpay.core.krist.registerMasterWalletMetrics
import io.sc3.kristpay.core.krist.ws.WebsocketManager
import io.sc3.kristpay.core.payment.Transactions
import io.sc3.kristpay.core.plugin.ServicePaymentPlanImpl
import io.sc3.kristpay.core.plugin.paymentGroupSubscriptions
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.core.welfare.WelfareType
import io.sc3.kristpay.fabric.command.transactionDescriptorHandlers
import io.sc3.kristpay.fabric.command.transactionSymbolHandlers
import io.sc3.kristpay.model.*
import io.sc3.kristpay.util.ClassScanner
import io.sc3.kristpay.util.drop
import io.sc3.kristpay.util.getOrMaybePut
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import io.prometheus.client.exporter.HTTPServer as PrometheusHTTPServer
import io.sc3.kristpay.model.Transaction as DBTransaction
import io.sc3.kristpay.model.Transactions as DBTransactions

internal lateinit var kpdb: Database

internal class KristPay(private val frontend: KristPayFrontend) : KristPayAPI {
    private var prometheusServer: HTTPServer? = null

    init {
        kpdb = ConnectionManager().database

        KristPayProvider.register(this)

        val tables = ClassScanner.getObjectsAnnotatedWith(KristPayModelTable::class, Table::class)

        transaction(kpdb) {
            SchemaUtils.createMissingTablesAndColumns(*tables.toTypedArray())
        }

        Transactions.restartPendingTransactions()

        WebsocketManager.start()
        Catchup.startCatchupLoop()

        // Setup prometheus
        registerMasterWalletMetrics()
        prometheusServer = PrometheusHTTPServer(CONFIG.prometheus.port)
    }

    override fun cleanup() {
        WebsocketManager.cleanup()
        Catchup.cleanup()

        prometheusServer?.let {
            logger.info("Shutting down Prometheus server")
            it.close()
        }
    }

    override suspend fun planServicePayment(request: ServicePaymentRequest): ServicePaymentPlan? {
        val response = frontend.presentPaymentRequest(request)
        if (response.confirmed) {
            return ServicePaymentPlanImpl(request, response)
        }

        return null
    }

    override fun subscribeToPaymentGroup(pluginID: PluginID, groupID: GroupID, callback: (ServicePayment) -> Unit)
        = paymentGroupSubscriptions.computeIfAbsent(Pair(pluginID, groupID)) { mutableListOf() }.add(callback).drop()

    override fun registerTransactionSymbol(callback: (KristPayWallet, TransactionSnapshot) -> TransactionSymbol?)
        = transactionSymbolHandlers.add(callback).drop()

    override fun registerTransactionDescriptor(callback: (KristPayWallet, TransactionSnapshot) -> List<String>?)
        = transactionDescriptorHandlers.add(callback).drop()

    override fun acquireServiceWallet(id: String): WalletID {
        val walletID = "service:$id"
        return transaction(kpdb) {
            Wallet.find { Wallets.name eq walletID }.firstOrNull()?.id ?:
                Wallet.new {
                    name = walletID
                }.id
        }.value
    }

    override fun getMasterWallet(): MasterWallet = MasterWalletImpl()

    override fun getWalletSnapshot(wallet: WalletID): WalletSnapshot? {
        logger.debug("Received snapshot request for wallet $wallet")
        return transaction(kpdb) {
            Wallet.findById(wallet)?.snapshot()
        }.also { logger.debug("Snapshot of $wallet was $it") }
    }

    override fun getWalletName(wallet: WalletID): String? {
        return walletNameCache.getOrMaybePut(wallet) {
            transaction(kpdb) {
                Wallet.findById(wallet)?.name
            }
        }
    }

    override fun findWalletByName(name: String): WalletSnapshot?
        = transaction(kpdb) { Wallet.find { Wallets.name eq name }.firstOrNull()?.snapshot() }
        .also { logger.info("Wallet Snapshot for $name was $it") }

    override fun getDefaultWallet(userID: UserID): WalletID?
        = transaction(kpdb) { User.findById(userID)?.defaultWalletId?.value }

    override fun getDefaultWalletSnapshot(userID: UserID): WalletSnapshot?
        = transaction(kpdb) { User.findById(userID)?.defaultWallet?.snapshot() }

    override fun getNotifications(userID: UserID): List<NotificationSnapshot>
        = transaction(kpdb) { Notification.find { Notifications.user eq userID }.map { it.toSnapshot() } }

    override fun clearNotifications(userID: UserID)
        = transaction(kpdb) { Notifications.deleteWhere { Notifications.user eq userID } }.drop()

    private fun checkWalletConflict(name: String, expectedId: WalletID?) {
        val conflictingWallet = Wallet.find { Wallets.name eq name }.firstOrNull()
        if (conflictingWallet != null && conflictingWallet.id.value != expectedId) {
            // Default to the wallet id if the name got taken over, it will be reset the next time the user logs in
            conflictingWallet.name = conflictingWallet.id.value.toString()
        }
    }

    override fun initializeUser(userID: UserID, userReferenceName: String, giveStartingBalance: Boolean): Boolean
        = transaction(kpdb) {
            val existingUser = User.findById(userID)
            if (existingUser != null) {
                // First, try to update a reset wallet name if applicable
                val wallet = existingUser.defaultWallet
                checkWalletConflict(userReferenceName, wallet.id.value)
                if (wallet.name != userReferenceName) {
                    wallet.name = userReferenceName
                }

                return@transaction false
            }

            val group = UserGroup.new(UUID.randomUUID()) {
                name = userID.toString()
                description = "Personal UserGroup for User $userID"
            }

            checkWalletConflict(userReferenceName, null)
            val wallet = Wallet.new(UUID.randomUUID()) {
                owner = group
                name = userReferenceName
            }

            val user = User.new(userID) {
                defaultWallet = wallet
                groups = SizedCollection(group)
            }

            WelfareBenefits.new {
                this.user = user
            }

            if (giveStartingBalance) {
                initializeTransaction(
                    initiator = Initiator.Server,
                    from = KristPayUnallocated,
                    to = KristPayWallet(wallet.id.value),
                    amount = MonetaryAmount(CONFIG.welfare.startingBalance.toInt()),
                    metadata = CommonMeta(
                        "message" to "Starting Balance"
                    ).toString(),
                    systemMetadata = buildJsonObject {
                        put(WELFARE_TX_CLASS, WelfareType.STARTING_BALANCE.name)
                    },
                    sendNotification = false // this is a starting balance transaction
                )
            }

            return@transaction true
        }

    override fun initializeTransaction(
        from: PaymentActor,
        to: PaymentActor,
        amount: MonetaryAmount,
        metadata: String?,
        initiator: Initiator,
        systemMetadata: JsonObject?,
        sendNotification: Boolean,
        externalReference: Int?,
        allowDebt: Boolean
    ): PaymentResult {
        val (paymentResult, tx) = Transactions.initializeTransaction(
            from,
            to,
            amount,
            metadata,
            initiator,
            systemMetadata,
            externalReference,
            allowDebt,
            sendNotification
        )

        transactionCounter.inc()
        if (paymentResult is PaymentResult.Error) {
            failedTransactionCounter.inc()
            logger.error("Error initializing transaction: $paymentResult")
        }

        return paymentResult
    }

    override fun listTransactions(wallet: WalletID, pageSize: Int): List<TransactionSnapshot> {
        val seq = DBTransaction.selectBuffered(DBTransactions.createdAt to SortOrder.DESC,
            {
                val a = DBTransactions.from eq KristPayWallet(wallet).serialize()
                val b = DBTransactions.to   eq KristPayWallet(wallet).serialize()

                a or b
            },
            pageSize
        )

        return seq.lazyMap { it.toSnapshot() }
    }

    override fun getTransaction(id: UUID): TransactionSnapshot? {
        return transaction(kpdb) {
            DBTransaction.findById(id)?.toSnapshot()
        }
    }

    override fun getFrontend() = frontend

    companion object: KLogging() {
        private val transactionCounter = Counter.build()
            .name("kristpay_transactions")
            .help("Number of transactions processed")
            .register()

        private val failedTransactionCounter = Counter.build()
            .name("kristpay_failed_transactions")
            .help("Number of transactions that failed to processed")
            .register()
    }
}
