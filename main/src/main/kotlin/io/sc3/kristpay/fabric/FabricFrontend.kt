/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalStdlibApi::class)

package io.sc3.kristpay.fabric

import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.KristPayFrontend
import io.sc3.kristpay.api.UserID
import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.api.model.frontend.PaymentRequestResponse
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.krist.CommonMeta
import io.sc3.kristpay.core.plugin.REQUESTER_NAME_ID
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.fabric.command.ListTransactionsCommand.formatActor
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.events.BalancerSender
import io.sc3.kristpay.fabric.events.welfare.WelfareHandlers
import io.sc3.kristpay.fabric.extensions.getString
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.fabric.text.buildSpacedText
import io.sc3.kristpay.fabric.text.buildText
import io.sc3.kristpay.fabric.text.primarySpacedText
import io.sc3.kristpay.model.User
import io.sc3.text.callback
import io.sc3.text.formatKristValue
import io.sc3.text.hover
import mu.KLogging
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting.BOLD
import net.minecraft.util.Formatting.ITALIC
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.jvm.optionals.getOrNull

class FabricFrontend(
    private val server: MinecraftServer
): KristPayConsumer(), KristPayFrontend {
    override suspend fun presentPaymentRequest(request: ServicePaymentRequest): PaymentRequestResponse {
        server.playerManager.getPlayer(request.fromUser)?.let { player ->
            player.sendMessage(of("Are you sure you would like to pay ", STYLE.warning)
                + formatKristValue(request.amount.amount)
                + of(" for ", STYLE.warning)
                + of(request.product.friendlyName, STYLE.accent)
                + of("?", STYLE.warning))

            return suspendCoroutine { co ->
                var continuation: Continuation<PaymentRequestResponse>? = co
                player.sendMessage(buildText {
                    +"\n   "
                    +of("[CONFIRM]", STYLE.primary + BOLD).callback(
                        owner = player,
                        name = "Confirm payment of ${request.amount.amount} for ${request.product.friendlyName}",
                        singleUse = true
                    ) {
                        val wallet = API.getDefaultWallet(request.fromUser)
                        // Don't send feedback here unlike cancel because we're going to send a notification through the
                        // PaymentPlan
                        continuation?.resume(PaymentRequestResponse(true, wallet))
                        continuation = null
                    }.hover(of("Click to confirm payment", STYLE.primary))
                    +"   "
                    +of("[CANCEL]", STYLE.cancel + BOLD).callback(
                        owner = player,
                        name = "Cancel payment of ${request.amount.amount} for ${request.product.friendlyName}",
                        singleUse = true
                    ) {
                        continuation?.apply {
                            resume(PaymentRequestResponse(false, null))
                            it.source.sendFeedback(
                                { of("Payment cancelled.", STYLE.cancel) },
                                false
                            )
                        }
                        continuation = null
                    }.hover(of("Click to cancel the payment", STYLE.cancel))
                })
            }
        }

        return PaymentRequestResponse(false, null)
    }

    override fun presentNotification(notification: NotificationSnapshot): Boolean {
        val player = server.playerManager.getPlayer(notification.user) ?: return false
        val tx = notification.referenceTransaction

        tx.systemMetadata?.let { meta ->
            for ((key, handler) in presentationOverrides) {
                if (meta.containsKey(key) && handler(notification, player)) {
                    return true
                }
            }
        }

        // Other participant will always be tx.from except when the user is not the initiator (i.e. a forced tx)
        val otherParticipant = if (tx.to == KristPayWallet(User.findById(notification.user)!!.defaultWalletId.value)) tx.from else tx.to
        val isForced = otherParticipant == tx.to && tx.from != tx.to // If the other participant is the recipient, the tx is forced

        if (isForced) {
            val to = formatActor(tx.to, null, tx)

            player.sendMessage(primarySpacedText {
                +of("You were forced to send")
                +formatKristValue(tx.amount.amount, formatting = STYLE.accent)
                +of("to")
                +to
            })
        } else {
            val from = when (val initiator = tx.initiator) {
                is Initiator.KristAddress -> {
                    val fromAddress = otherParticipant as KristAddress
                    if (initiator.address == fromAddress.address) {
                        of(initiator.address, STYLE.address)
                    } else {
                        of(initiator.address, STYLE.address) + of(" (", STYLE.primary) + of(fromAddress.address, STYLE.address) + of(")", STYLE.primary)
                    }
                }
                is Initiator.User -> {
                    server.playerManager.getPlayer(initiator.id)?.displayName?.copy()
                        ?: server.userCache?.getByUuid(initiator.id)?.getOrNull()?.let { of(it.name, STYLE.address) }
                        ?: of("Unknown User", STYLE.warning + ITALIC).hover(of(initiator.id.toString()))
                }
                is Initiator.ServerPlugin -> {
                    tx.systemMetadata?.getString(REQUESTER_NAME_ID)?.let { of(it, STYLE.address) }
                        ?: of("Unknown Plugin", STYLE.warning + ITALIC).hover(of(initiator.pluginID))
                }
                Initiator.Server -> of(CONFIG.welfare.bankName, STYLE.address)
            }

            player.sendMessage(of("", STYLE.primary) + from + of(" sent you ", STYLE.primary) + formatKristValue(tx.amount.amount, formatting = STYLE.accent))
        }

        generateSpecialText(tx).forEach {
            player.sendMessage(it)
        }

        return true
    }

    override fun sendBalance(user: UserID, amount: MonetaryAmount) {
        server.execute {
            val player = server.playerManager.getPlayer(user)
            if (player == null) {
                logger.debug("Tried to send balance update to player $user, who is not online")
                return@execute
            }

            BalancerSender.sendBalance(player)
        }
    }

    override fun notifyServicePaymentSuccess(request: ServicePaymentRequest, response: PaymentRequestResponse) {
        val player = server.playerManager.getPlayer(request.fromUser) ?: return
        player.sendMessage(of("Payment successful!", STYLE.primary))
    }

    override fun notifyServicePaymentFailure(result: PaymentResult, request: ServicePaymentRequest, response: PaymentRequestResponse) {
        val player = server.playerManager.getPlayer(request.fromUser) ?: return
        when (result) {
            PaymentResult.InsufficientFunds -> player.sendMessage(of("You don't have enough funds to make this payment.", STYLE.error))
            PaymentResult.Rejected -> { /* No-op - service payment was rejected, so it should show its own error. */ }
            else -> player.sendMessage(of("Payment failed due to an internal error!", STYLE.error))
        }
    }

    companion object: KLogging() {
        private val presentationOverrides = mutableMapOf<String, (notification: NotificationSnapshot, player: ServerPlayerEntity) -> Boolean>(
            WELFARE_TX_CLASS to WelfareHandlers::handleNotificationPresentation
        )

        // Generates any "special text" for metadata, i.e. the "message" and "error" text
        fun generateSpecialText(tx: TransactionSnapshot): List<MutableText> {
            val metadata = tx.metadata ?: return emptyList()
            val commonMeta = CommonMeta(metadata)

            return listOfNotNull(
                commonMeta.message?.let { buildSpacedText { +STYLE.primary + of("Message:", STYLE.secondary) + it } },
                commonMeta.error  ?.let { buildSpacedText { +STYLE.error   + of("Error:",   STYLE.danger)    + it } },
            )
        }
    }
}
