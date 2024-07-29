/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.payment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mu.KLoggable
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.NotificationSnapshot
import io.sc3.kristpay.api.model.PaymentActor
import io.sc3.kristpay.api.model.PaymentResult
import io.sc3.kristpay.api.model.TransactionSnapshot
import io.sc3.kristpay.api.model.TransactionState
import io.sc3.kristpay.core.AlarmGateway
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.fabric.extensions.getString
import io.sc3.kristpay.model.InflightTransaction
import io.sc3.kristpay.model.Notification
import io.sc3.kristpay.model.Transaction
import io.sc3.kristpay.model.Wallet
import io.sc3.kristpay.util.supervised
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

val FlyingTransactionScope = CoroutineScope(Dispatchers.IO.supervised())

val StateMachines: MutableSet<UUID> = Collections.newSetFromMap(ConcurrentHashMap())
val CompletionFutures: MutableMap<UUID, CompletableFuture<PaymentResult>> = ConcurrentHashMap()

const val FAILURE_REASON_KEY = "failureReason"

object Transactions: KristPayConsumer(), KLoggable by KLogging() {
    const val KRIST_TX_REF_KEY = "ref"

    sealed interface StateMachineAction
    data class ProceedToState(val state: TransactionState): StateMachineAction
    data class QuitToPending(val pending: PaymentResult.Pending): StateMachineAction
    data class QuitToError(val failure: PaymentResult.Failure): StateMachineAction
    object QuitToComplete: StateMachineAction

    sealed interface GraphNode
    data class Node(
        val action: (tx: Transaction) -> PaymentResult,
        val successState: TransactionState,
        val pendingState: TransactionState,
        val failureState: TransactionState,
        val fatalFailureState: TransactionState = failureState
    ): GraphNode

    data class ConstantNode(
        val action: StateMachineAction
    ): GraphNode

    object FailureNode: GraphNode

    fun restartPendingTransaction(txSnapshot: TransactionSnapshot) {
        if (StateMachines.contains(txSnapshot.id)) {
            logger.warn { "Transaction ${txSnapshot.id} is already in progress, skipping" }
            return
        }

        FlyingTransactionScope.launch {
            val paymentResult = transaction(kpdb) {
                val tx = Transaction.findById(txSnapshot.id) ?: run {
                    InflightTransaction.findById(txSnapshot.id)?.delete()
                    return@transaction PaymentResult.Success
                }

                runStateMachine(tx, ProceedToState(tx.state))
            }

            if (paymentResult is PaymentResult.Pending) {
                watchPendingStateMachine(txSnapshot, paymentResult)
            }
        }
    }

    fun restartPendingTransactions() {
        val txs = transaction(kpdb) {
            InflightTransaction.all().map { it.transaction.toSnapshot() }
        }

        txs.forEach { txSnapshot ->
            restartPendingTransaction(txSnapshot)
        }
    }

    fun initializeTransaction(
        from: PaymentActor,
        to: PaymentActor,
        amount: MonetaryAmount,
        metadata: String? = null,
        initiator: Initiator,
        systemMetadata: JsonObject? = null,
        externalReference: Int? = null,
        allowDebt: Boolean = false,
        sendNotification: Boolean = true,
    ): Pair<PaymentResult, TransactionSnapshot> {
        val (txSnapshot, paymentResult) = transaction(kpdb) {
            val tx = Transaction.new(UUID.randomUUID()) {
                state = TransactionState.INIT

                this.from = from
                this.to = to
                this.amount = amount.amount

                this.initiator = initiator
                this.metadata = metadata
                this.systemMetadata = systemMetadata
                this.notify = sendNotification

                this.externalReference = externalReference
            }

            InflightTransaction.new(tx.id.value) {
                transaction = tx
            }

            val initNode = Node(
                { runInit(it, allowDebt) },
                TransactionState.DEBIT_SUCCESS,
                TransactionState.DEBIT_PENDING,
                TransactionState.FAILED
            )

            val result = runStateMachine(tx, getStateMachineAction(initNode, initNode.action(tx), tx))

            tx.toSnapshot() to result
        }

        runPostStateMachine(txSnapshot)

        if (paymentResult is PaymentResult.Pending) {
            val future = CompletableFuture<PaymentResult>()
            CompletionFutures[txSnapshot.id] = future
            watchPendingStateMachine(txSnapshot, paymentResult)
            return PaymentResult.Pending(future) to txSnapshot
        }

        return paymentResult to txSnapshot
    }

    private fun watchPendingStateMachine(txSnapshot: TransactionSnapshot, paymentResult: PaymentResult.Pending) {
        FlyingTransactionScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    paymentResult.future.get(10, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    logger.error(e) { "Transaction ${txSnapshot.id} timed out, retrying..." }

                    // Re-run the step
                    val newPaymentResult = transaction(kpdb) {
                        val tx = Transaction.findById(txSnapshot.id) ?: run {
                            InflightTransaction.findById(txSnapshot.id)?.delete()
                            return@transaction PaymentResult.Success
                        }

                        runStateMachine(tx, ProceedToState(tx.state))
                    }

                    return@withContext newPaymentResult
                }
            }

            if (result is PaymentResult.Pending) {
                return@launch watchPendingStateMachine(txSnapshot, paymentResult)
            }

            // Resume the state machine
            val afterSnapshot = transaction(kpdb) {
                val tx = Transaction.findById(txSnapshot.id) ?: throw IllegalStateException("Transaction ${txSnapshot.id} not found")
                val action = when (result) {
                    is PaymentResult.Failure ->
                        // Route to failure node
                        getStateMachineAction(getActionFromState(tx.state) as Node, result, tx)

                    // Run pending one more time TODO: this is a bit hacky
                    else -> ProceedToState(tx.state)
                }

                runStateMachine(tx, action)

                tx.toSnapshot()
            }

            runPostStateMachine(afterSnapshot)
        }
    }

    private fun runPostStateMachine(tx: TransactionSnapshot) = transaction(kpdb) {
        val frontend = API.getFrontend()

        val from = tx.from
        if (from is KristPayWallet) {
            val fromWallet = Wallet.findById(from.walletId)!!
            fromWallet.owner?.members?.forEach {
                // TODO - support multiple wallets
                frontend.sendBalance(it.id.value, MonetaryAmount(fromWallet.balance))

                // If the paying user was not the initiator, we need to notify the user that they were forced to pay
                if (tx.initiator != Initiator.User(it.id.value)) {
                    if (tx.notify && !frontend.presentNotification(NotificationSnapshot(it.id.value, tx))) {
                        Notification.new {
                            user = it
                            referenceTransaction = Transaction.findById(tx.id)!!
                        }
                    }
                }
            }
        }

        val to = tx.to
        if (to is KristPayWallet) {
            val toWallet = Wallet.findById(to.walletId)!!
            toWallet.owner?.members?.forEach {
                if (tx.notify && !frontend.presentNotification(NotificationSnapshot(it.id.value, tx))) {
                    Notification.new {
                        user = it
                        referenceTransaction = Transaction.findById(tx.id)!!
                    }
                }

                // TODO - support multiple wallets
                frontend.sendBalance(it.id.value, MonetaryAmount(toWallet.balance))
            }
        }
    }

    private fun getActionFromState(state: TransactionState): GraphNode = when (state) {
        TransactionState.INIT -> throw IllegalStateException("Cannot get action from INIT state")

        TransactionState.DEBIT_PENDING -> Node(
            ::checkPendingDebit,
            TransactionState.DEBIT_SUCCESS,
            TransactionState.DEBIT_PENDING,
            TransactionState.FAILED
        )
        TransactionState.DEBIT_SUCCESS -> Node(
            ::runCredit,
            TransactionState.CREDIT_SUCCESS,
            TransactionState.CREDIT_PENDING,
            TransactionState.RETRY_CREDIT,
            TransactionState.CREDIT_FAILED
        )

        TransactionState.CREDIT_PENDING -> Node(
            ::checkPendingCredit,
            TransactionState.CREDIT_SUCCESS,
            TransactionState.CREDIT_PENDING,
            TransactionState.RETRY_CREDIT,
            TransactionState.CREDIT_FAILED
        )

        TransactionState.RETRY_CREDIT -> Node(
            ::retryCredit,
            TransactionState.CREDIT_SUCCESS,
            TransactionState.CREDIT_PENDING,
            TransactionState.RETRY_CREDIT,
            TransactionState.CREDIT_FAILED
        )

        TransactionState.CREDIT_SUCCESS -> ConstantNode(ProceedToState(TransactionState.COMPLETE))
        TransactionState.CREDIT_FAILED -> //ConstantNode(ProceedToState(TransactionState.FAILED))
        Node(
            ::runDebitReversal,
            TransactionState.REFUND_DEBIT_SUCCESS,
            TransactionState.REFUND_DEBIT_PENDING,
            TransactionState.FAILED // Ouchie
        )

//        // TODO: Implement this
//        TransactionState.CREDIT_FAILED -> Node(
//            ::verifyWithCatchup,
//            TransactionState.CREDIT_SUCCESS,
//            TransactionState.CREDIT_PENDING,
//            TransactionState.CREDIT_FAILED_CONFIRMED
//        )
//
//        TransactionState.CREDIT_FAILED_CONFIRMED -> Node(
//            ::runDebitReversal,
//            TransactionState.REFUND_DEBIT_SUCCESS,
//            TransactionState.REFUND_DEBIT_PENDING,
//            TransactionState.FAILED // Ouchie
//        )

        TransactionState.REFUND_DEBIT_PENDING -> Node(
            ::checkPendingDebitReversal,
            TransactionState.FAILED,
            TransactionState.REFUND_DEBIT_PENDING,
            TransactionState.FAILED // Ouchie
        )
        TransactionState.REFUND_DEBIT_SUCCESS -> ConstantNode(ProceedToState(TransactionState.FAILED))

        TransactionState.COMPLETE -> ConstantNode(QuitToComplete)
        TransactionState.FAILED -> FailureNode
    }

    private fun getStateMachineAction(node: Node, result: PaymentResult, tx: Transaction): StateMachineAction {
        return when (result) {
            is PaymentResult.Pending -> {
                tx.state = node.pendingState
                QuitToPending(result)
            }

            is PaymentResult.Success -> {
                tx.state = node.successState
                ProceedToState(tx.state)
            }

            is PaymentResult.Failure -> {
                tx.state = when (result) {
                    is PaymentResult.PermanentFailure -> node.fatalFailureState
                    else -> node.failureState
                }

                // Gross...
                val meta = tx.systemMetadata?.toMutableMap() ?: mutableMapOf()
                meta[FAILURE_REASON_KEY] = JsonPrimitive(result.serialize())
                tx.systemMetadata = JsonObject(meta)

                ProceedToState(tx.state)
            }
        }
    }

    private fun runStateMachine(tx: Transaction, action: StateMachineAction): PaymentResult {
        StateMachines.add(tx.id.value)

        return when (action) {
            is ProceedToState -> runStateMachine(tx, when (val node = getActionFromState(action.state)) {
                is ConstantNode -> {
                    tx.state = action.state
                    node.action
                }
                is Node -> getStateMachineAction(node, node.action(tx), tx)
                is FailureNode -> {
                    tx.state = TransactionState.FAILED
                    QuitToError(
                        tx.systemMetadata?.getString(FAILURE_REASON_KEY)?.let {
                            PaymentResult.Failure.deserialize(it).also { failure ->
                                if (failure is PaymentResult.Error) alarmFailure(tx)
                            }
                        } ?: PaymentResult.Error("Unknown error")
                    )
                }
            })

            is QuitToPending -> action.pending
            is QuitToError -> {
                InflightTransaction.findById(tx.id)?.delete()
                StateMachines.remove(tx.id.value)
                CompletionFutures[tx.id.value]?.complete(action.failure)
                CompletionFutures.remove(tx.id.value)

                action.failure
            }
            QuitToComplete -> {
                InflightTransaction.findById(tx.id)?.delete()
                StateMachines.remove(tx.id.value)
                CompletionFutures[tx.id.value]?.complete(PaymentResult.Success)
                CompletionFutures.remove(tx.id.value)

                PaymentResult.Success
            }
        }
    }

    private fun alarmFailure(tx: Transaction) = AlarmGateway.sendAlert(
        title = "Transaction failed",
        description = "Transaction ${tx.id} failed with reason: ${tx.systemMetadata?.getString(FAILURE_REASON_KEY)}",
    )

    private fun runInit(tx: Transaction, allowDebt: Boolean): PaymentResult {
        val source = PaymentStrategy[tx.from]

        logger.info("Requesting debit from $source")
        return runBlocking {
            source.debit(MonetaryAmount(tx.amount), tx, allowDebt)
        }
    }

    private fun checkPendingDebit(tx: Transaction): PaymentResult {
        val source = PaymentStrategy[tx.from]

        logger.info("Checking debit from $source")
        return runBlocking {
            source.checkPendingDebit(tx)
        }
    }

    private fun runCredit(tx: Transaction): PaymentResult {
        val sink = PaymentStrategy[tx.to]

        logger.info("Requesting credit to $sink")
        return runBlocking {
            sink.credit(MonetaryAmount(tx.amount), tx)
        }
    }

    private fun retryCredit(tx: Transaction): PaymentResult {
        val sink = PaymentStrategy[tx.to]

        // Check that we haven't exceeded the retry limit
        tx.attempt = (tx.attempt ?: 0) + 1

        logger.info("Retrying credit to $sink")
        return runBlocking {
            sink.credit(MonetaryAmount(tx.amount), tx)
        }
    }

    private fun runDebitReversal(tx: Transaction): PaymentResult {
        val source = PaymentStrategy[tx.from]

        // TODO - send notification that it was refunded

        logger.info("Requesting debit reversal from $source")
        return runBlocking {
            source.credit(MonetaryAmount(tx.amount), tx)
        }
    }

    private fun checkPendingDebitReversal(tx: Transaction): PaymentResult {
        val source = PaymentStrategy[tx.from]

        logger.info("Checking debit reversal from $source")
        return runBlocking {
            source.checkPendingCredit(tx)
        }
    }

    private fun checkPendingCredit(tx: Transaction): PaymentResult {
        val sink = PaymentStrategy[tx.to]

        logger.info("Checking credit to $sink")
        return runBlocking {
            sink.checkPendingCredit(tx)
        }
    }
}
