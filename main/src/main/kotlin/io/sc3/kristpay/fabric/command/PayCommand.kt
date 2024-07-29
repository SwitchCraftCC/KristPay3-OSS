/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.WalletID
import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.krist.CommonMeta
import io.sc3.kristpay.core.krist.KeyedValue
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.fabric.extensions.toText
import io.sc3.kristpay.fabric.shared.argument.ArgumentTypeRegistrar
import io.sc3.kristpay.fabric.shared.argument.PayableArgumentType
import io.sc3.kristpay.fabric.text.buildSpacedText
import io.sc3.kristpay.util.unreachable
import io.sc3.text.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@KristPayCommand
object PayCommand : KristPayConsumer(), CommandObject {
    object Permission {
        private const val ROOT = "kristpay.pay"
        const val PAY_ADDRESS  = "$ROOT.address"
        const val PAY_ADDRESS_WITH_METADATA  = "$PAY_ADDRESS.metadata"
    }

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        dispatcher.register(
            literal("pay")
                .requiresPermission(Permission.PAY_ADDRESS, 0)
                .then(argument("address", PayableArgumentType.payable()).suggests(ArgumentTypeRegistrar.PayableArgumentSuggestionProvider)
                    .then(argument("amount", IntegerArgumentType.integer(1))
                        .executesAsync(::pay)
                        .then(argument("metadata", StringArgumentType.greedyString())
                            .requiresPermission(Permission.PAY_ADDRESS_WITH_METADATA, 0)
                            .executesAsync(::payWithMetadata)
                        )
                    ))
        )
    }

    private fun Char.isPrintable() = this in ' '..'~'

    private suspend fun payWithMetadata(commandContext: CommandContext<ServerCommandSource>) {
        val metadata = StringArgumentType.getString(commandContext, "metadata").filter { it.isPrintable() }
        val commonMeta = CommonMeta(metadata)
        val filteredMeta = CommonMeta(
            commonMeta.tags.filterNot { (it as? KeyedValue)?.value?.first?.trim() in setOf("ref", "return", "username") }
        )

        val metaString = filteredMeta.toString()
        if (metaString.length > 100) {
            commandContext.source.sendError(of("Metadata is too long! It must be 100 characters or less."))
            return
        }

        return pay(commandContext, metaString)
    }

    private suspend fun pay(commandContext: CommandContext<ServerCommandSource>, metadata: String? = null) {
        val payable = PayableArgumentType.getPayable(commandContext, "address")
        val amount = MonetaryAmount(IntegerArgumentType.getInteger(commandContext, "amount"))

        val to = when (payable) {
            is PayableArgumentType.Name -> KristAddress(payable.format())
            is PayableArgumentType.Address -> KristAddress(payable.address)
            is PayableArgumentType.Username -> KristPayWallet(
                findWallet(commandContext, payable.username) ?: run {
                    commandContext.source.sendFeedback(
                        { of("Unable to find player ", STYLE.primary) + of(payable.username, STYLE.accent) },
                        false
                    )

                    return
                }
            )
            is PayableArgumentType.AddressOrPlayer -> findWallet(commandContext, payable.target)
                ?.let { KristPayWallet(it) } ?: KristAddress(payable.target)
        }

        val from = API.getDefaultWalletSnapshot(commandContext.source.playerOrThrow.uuid)!!
        val player = commandContext.source.playerOrThrow
        val username = player.entityName.lowercase()

        checkTransaction(commandContext, from, amount) {
            var result = API.initializeTransaction(
                from = KristPayWallet(from.id),
                to = to,
                amount = amount,
                metadata = listOfNotNull(CommonMeta(
                    "return" to "$username@${CONFIG.krist.advertisedName}.kst",
                    "username" to username,
                    "useruuid" to player.uuid.toString()
                ).toString(), metadata).joinToString(";"),
                initiator = Initiator.User(commandContext.source.playerOrThrow.uuid),
                systemMetadata = buildJsonObject {
                    put("command", "pay")
                },
                sendNotification = true
            )

            var notifiedUser = false
            while (result is PaymentResult.Pending) {
                result = try {
                    result.future.get(3, TimeUnit.SECONDS)
                } catch (e: CancellationException) {
                    PaymentResult.Error("Transaction cancelled")
                } catch (e: TimeoutException) {
                    if (!notifiedUser) {
                        notifiedUser = true
                        commandContext.source.sendFeedback({
                            of("Transaction is pending... please wait a moment...", STYLE.note)
                        }, true)
                    }

                    result
                }
            }

            when (result) {
                is PaymentResult.Success -> {
                    commandContext.source.sendFeedback({
                        success() + of("Paid ", STYLE.primary) + amount.toText() + of(" to ") + of(payable.format(), STYLE.address)
                    }, false)
                }

                PaymentResult.InsufficientFunds -> {
                    commandContext.source.sendFeedback({
                        of("You do not have enough funds to pay this amount", STYLE.error)
                    }, false)
                }

                PaymentResult.InvalidName -> {
                    commandContext.source.sendFeedback(
                        {
                            buildSpacedText { +STYLE.error
                                +"The name"
                                +of((payable as PayableArgumentType.Name).name + ".kst", STYLE.accent)
                                +"is not registered, please check your spelling."
                            }
                        },
                        false
                    )
                }

                is PaymentResult.Failure -> {
                    commandContext.source.sendFeedback({
                        of("There was an error executing the transaction; please contact an administrator", STYLE.error)
                    }, true)
                }

                else -> unreachable
            }
        }
    }

    private fun checkTransaction(
        commandContext: CommandContext<ServerCommandSource>,
        from: WalletSnapshot,
        amount: MonetaryAmount,
        accept: () -> Unit
    ) {
        if (amount > from.balance) {
            commandContext.source.sendFeedback({
                of("You do not have enough funds to pay this amount", STYLE.error)
            }, false)

            return
        }

        val warning = buildSpacedText {
            +STYLE.accent
            +of("Warning:", STYLE.warning)
            +"You're about to make a very large transaction"

            val warnThreshold = CONFIG.frontend.payWarningThreshold.toInt()

            if (amount == from.balance) +"(your entire balance!)"
            else if (amount >= from.balance * 0.5) +"(more than half your balance!)"
            else if (amount >= MonetaryAmount(warnThreshold)) {
                +"(more than" + formatKristValue(warnThreshold) - "!)"
            } else {
                return accept() // No warning needed
            }

            -"."

            val callback = CallbackCommand.makeCommand(
                owner = commandContext.source.playerOrThrow.uuid,
                name = "Confirm transaction",
                singleUse = true
            ) { commandScope.launch { accept() } }
            +of("Click here", STYLE.confirm).hover(
                of("Confirm transaction ", STYLE.warning) + of("(dangerous!)", STYLE.danger)
            ).runCommand(callback)
            +"to confirm this transaction."
        }

        commandContext.source.sendFeedback({ warning }, false)
    }

    private fun findWallet(
        commandContext: CommandContext<ServerCommandSource>,
        name: String
    ): WalletID? {
        val profile = commandContext.source.server.userCache?.findByName(name)
        val wallet = profile?.map { API.getDefaultWallet(it.id) }
        return wallet?.orElse(null)
    }
}
