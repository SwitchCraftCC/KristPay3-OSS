/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import mu.KLoggable
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.WalletID
import io.sc3.kristpay.api.model.GroupID
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.PaymentResult
import io.sc3.kristpay.api.model.PluginID
import io.sc3.kristpay.api.model.ServicePayment
import io.sc3.kristpay.api.model.ServicePaymentPlan
import io.sc3.kristpay.api.model.ServicePaymentRequest
import io.sc3.kristpay.api.model.frontend.PaymentRequestResponse
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.payment.Transactions
import io.sc3.kristpay.core.welfare.IS_REVERSED
import io.sc3.kristpay.core.welfare.REVERSAL_OF
import io.sc3.kristpay.model.PluginOrder
import io.sc3.kristpay.model.Transaction
import java.util.UUID

const val REQUESTER_NAME_ID = "requesterName"
const val ASSOCIATED_ORDER_ID = "associatedOrder"
const val PRODUCT_ID = "product"

internal val paymentGroupSubscriptions = mutableMapOf<Pair<PluginID, GroupID>, MutableList<(ServicePayment) -> Unit>>()

class ServicePaymentPlanImpl(
    private val request: ServicePaymentRequest,
    private val response: PaymentRequestResponse
): ServicePaymentPlan, KristPayConsumer(), KLoggable by KLogging() {
    override fun execute(recipientWallet: WalletID) {
        val frontend = API.getFrontend()

        val orderID = UUID.randomUUID()
        val (result, tx) = Transactions.initializeTransaction(
            from = KristPayWallet(response.chosenWallet!!),
            to = KristPayWallet(recipientWallet),
            amount = request.amount,
            initiator = Initiator.User(request.fromUser),
            systemMetadata = buildJsonObject {
                put(REQUESTER_NAME_ID, request.requesterName)
                put(ASSOCIATED_ORDER_ID, orderID.toString())
                put(PRODUCT_ID, Json.encodeToJsonElement(request.product))
            }
        )

        if (result != PaymentResult.Success) {
            frontend.notifyServicePaymentFailure(result, request, response)
            return
        }

        transaction(kpdb) {
            PluginOrder.new(orderID) {
                product = request.product
                associatedTransaction = Transaction.findById(tx.id)!!
                accepted = false
            }
        }

//        Transactions.notifyTransaction(frontend, tx, true)

        for (subscription in paymentGroupSubscriptions[Pair(request.pluginID, request.product.groupID)]!!) {
            var accepted = false

            try {
                subscription.invoke(object : ServicePayment {
                    override val product = request.product
                    override val transaction = tx

                    override fun accept() {
                        accepted = true
                    }

                    override fun reject() {
                        accepted = false
                    }
                })
            } catch (e: Exception) {
                logger.error(e) { "Error while executing payment subscription" }
                frontend.notifyServicePaymentFailure(PaymentResult.Error(e.message ?: "Unknown Error"), request, response)
                return
            }

            if (accepted) {
                // We're done here
                frontend.notifyServicePaymentSuccess(request, response)
                return transaction(kpdb) {
                    PluginOrder.findById(orderID)!!.accepted = true
                }
            }
        }

        // No one accepted the payment, so we have to reverse it
        transaction(kpdb) {
            val order = PluginOrder.findById(orderID)!!
            order.associatedTransaction.systemMetadata = buildJsonObject {
                put(REQUESTER_NAME_ID, request.requesterName)
                put(ASSOCIATED_ORDER_ID, orderID.toString())
                put(PRODUCT_ID, Json.encodeToJsonElement(request.product))
                put(IS_REVERSED, true)
            }

            Transactions.initializeTransaction(
                from = KristPayWallet(recipientWallet),
                to = KristPayWallet(response.chosenWallet!!),
                amount = request.amount,
                initiator = Initiator.ServerPlugin(request.pluginID),
                systemMetadata = buildJsonObject {
                    put(REQUESTER_NAME_ID, request.requesterName)
                    put(ASSOCIATED_ORDER_ID, orderID.toString())
                    put(PRODUCT_ID, Json.encodeToJsonElement(request.product))
                    putJsonArray(REVERSAL_OF) {
                        add(order.associatedTransaction.id.value.toString())
                    }
                }
            )
        }

        frontend.notifyServicePaymentFailure(PaymentResult.Rejected, request, response)
    }
}
