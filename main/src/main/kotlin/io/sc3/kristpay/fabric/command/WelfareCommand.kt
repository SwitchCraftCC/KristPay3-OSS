/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.authlib.GameProfile
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.WalletID
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.KristPayUnallocated
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.welfare.IS_REVERSED
import io.sc3.kristpay.core.welfare.REVERSAL_OF
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.core.welfare.WelfareType
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.events.welfare.WelfareHandlers
import io.sc3.kristpay.fabric.extensions.getBoolean
import io.sc3.kristpay.fabric.extensions.getString
import io.sc3.kristpay.fabric.extensions.note
import io.sc3.kristpay.fabric.extensions.toText
import io.sc3.kristpay.fabric.text.buildSpacedText
import io.sc3.kristpay.fabric.text.primarySpacedText
import io.sc3.kristpay.model.Transaction
import io.sc3.kristpay.model.Transactions
import io.sc3.text.*
import kotlinx.serialization.json.*
import mu.KLoggable
import mu.KLogging
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Formatting
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

@KristPayCommand
object WelfareCommand : KristPayConsumer(), CommandObject, KLoggable by KLogging() {
    object Permission {
        private const val ROOT = "kristpay.welfare"
        const val OPT_OUT       = "$ROOT.opt.out.base"
        const val OPT_IN        = "$ROOT.opt.in.base"
        const val OPT_OUT_OTHER = "$ROOT.opt.out.others"
        const val OPT_IN_OTHER  = "$ROOT.opt.in.others"

        const val CHECK         = "$ROOT.check.base"
        const val CHECK_OTHER   = "$ROOT.check.others"

        const val RETURN        = "$ROOT.return.base"
        const val RETURN_OTHER  = "$ROOT.return.others"
    }

    private fun     checkStatus(ctx: CommandContext<ServerCommandSource>) {
        val isEnabled = WelfareHandlers.getWelfareEnabled(ctx.source.playerOrThrow.uuid)
        ctx.source.sendFeedback({
            of("You are currently opted ", STYLE.primary) +
                of(if (isEnabled) "in " else "out ", STYLE.accent) +
                of(if (isEnabled) "to " else "of ", STYLE.primary) +
                of("the welfare program.", STYLE.primary)
        }, false)
    }

    private fun checkOtherStatus(ctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(ctx, "player")

        players.forEach { player ->
            val isEnabled = WelfareHandlers.getWelfareEnabled(player.id)
            ctx.source.sendFeedback({
                of(player.name, STYLE.accent) + of(" is currently opted ", STYLE.primary) +
                    of(if (isEnabled) "in " else "out ", STYLE.accent) +
                    of(if (isEnabled) "to " else "of ", STYLE.primary) +
                    of("the welfare program.", STYLE.primary)
            }, false)
        }
    }


    private fun optIn(ctx: CommandContext<ServerCommandSource>) {
        if (WelfareHandlers.setWelfareToggle(ctx.source.playerOrThrow.uuid, true)) {
            ctx.source.sendFeedback({
                success() + of("You have opted ", STYLE.primary) + of("in ", STYLE.accent) + of("to the welfare program.", STYLE.primary)
            }, true)
        } else {
            ctx.source.sendFeedback({
                of("You are already opted ", STYLE.error) + of("in ", STYLE.accent) + of("to the welfare program.", STYLE.error)
            }, false)
        }
    }

    private fun optInOther(ctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(ctx, "player")

        players.forEach { player ->
            if (WelfareHandlers.setWelfareToggle(player.id, true)) {
                ctx.source.sendFeedback({
                    success() + of(player.name, STYLE.accent) + of(" has opted ", STYLE.primary) + of("in ", STYLE.accent) + of("to the welfare program.", STYLE.primary)
                }, true)
            } else {
                ctx.source.sendFeedback({
                    of(player.name, STYLE.accent) + of(" is already opted ", STYLE.error) + of("in ", STYLE.accent) + of("to the welfare program.", STYLE.error)
                }, false)
            }
        }
    }

    private fun optOut(ctx: CommandContext<ServerCommandSource>) {
        if (WelfareHandlers.setWelfareToggle(ctx.source.playerOrThrow.uuid, false)) {
            ctx.source.sendFeedback({
                success() + of("You have opted ", STYLE.primary) + of("out ", STYLE.accent) + of("of the welfare program.", STYLE.primary) +
                        note() + of("If you are opting out to comply with the ", STYLE.note) +
                        openableLink("alt policy", link = "https://rules.switchcraft.pw/#alts") +
                        of(" please ensure you return all previous welfare rewards and the starting balance to the server." +
                                " You can run ", STYLE.note) + runnableCommand("/welfare return", cmd = "/welfare return") +
                        of(" to do so.", STYLE.note)
            }, true)
        } else {
            ctx.source.sendFeedback({
                of("You are already opted ", STYLE.error) + of("out ", STYLE.accent) + of("of the welfare program.", STYLE.error)
            }, false)
        }
    }

    private fun optOutOther(ctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(ctx, "player")

        players.forEach { player ->
            if (WelfareHandlers.setWelfareToggle(player.id, false)) {
                ctx.source.sendFeedback({
                    success() + of(player.name, STYLE.accent) + of(" has opted ", STYLE.primary) + of("out ", STYLE.accent) + of("of the welfare program.", STYLE.primary) +
                            note() + of("If they are opting out to comply with the ", STYLE.note) +
                            openableLink("alt policy", link = "https://rules.switchcraft.pw/#alts") +
                            of(" please ensure they return all previous welfare rewards and the starting balance to the server." +
                                    " You can run ", STYLE.note) + runnableCommand("/welfare return ${player.name}", cmd = "/welfare return ${player.name}") +
                            of(" to do so.", STYLE.note)
                }, true)
            } else {
                ctx.source.sendFeedback({
                    of(player.name, STYLE.accent) + of(" is already opted ", STYLE.error) + of("out ", STYLE.accent) + of("of the welfare program.", STYLE.error)
                }, false)
            }
        }
    }

    /** Must be ran inside a DB transaction. */
    private fun findWelfareTransactions(player: GameProfile): Pair<WalletID, List<Transaction>> {
        logger.info("Returning welfare for ${player.name}")
        val wallet = API.getDefaultWallet(player.id)!!
        val transactions = Transaction.find {
            val a = Transactions.from eq KristPayUnallocated.serialize()
            val b = Transactions.to   eq KristPayWallet(wallet).serialize()

            a and b
        }

        logger.info("Found ${transactions.count()} deposit transactions for ${player.name}")

        val welfareTxs = transactions.filter {
            it.systemMetadata?.getString(WELFARE_TX_CLASS) != null &&
                it.systemMetadata?.getBoolean(IS_REVERSED) != true
        }

        logger.info("Found ${welfareTxs.count()} welfare transactions to return for ${player.name}")
        return wallet to welfareTxs
    }

    private fun returnWelfare(player: GameProfile, ctx: CommandContext<ServerCommandSource>) = transaction(kpdb) {
        val isForced = player.id != ctx.source.player?.uuid
        val (wallet, welfareTxs) = findWelfareTransactions(player)

        if (welfareTxs.isEmpty()) {
            if (isForced) {
                ctx.source.sendFeedback({
                    buildSpacedText {
                        +STYLE.error
                        +"No welfare transactions found for"
                        +of(player.name, STYLE.accent) - "."
                    }
                }, false)
            } else {
                ctx.source.sendFeedback(
                    { of("You have no welfare rewards to return.", STYLE.error) },
                    false
                )
            }

            return@transaction
        }

        val total = welfareTxs.sumOf { it.amount }

        logger.info("Total welfare amount to return for ${player.name}: $total")

        welfareTxs.forEach { it.systemMetadata = JsonObject(it.systemMetadata!!.plus(Pair(IS_REVERSED, JsonPrimitive(true)))) }

        logger.info("Reversed all welfare transactions for ${player.name}")

        API.initializeTransaction(
            from = KristPayWallet(wallet),
            to = KristPayUnallocated,
            amount = MonetaryAmount(total),
            initiator = ctx.source.player?.let { Initiator.User(it.uuid) } ?: Initiator.Server,
            systemMetadata = buildJsonObject {
                put(WELFARE_TX_CLASS, WelfareType.REVERSAL.name)
                putJsonArray(REVERSAL_OF) {
                    welfareTxs.forEach { add(it.id.value.toString()) }
                }
            },
            allowDebt = true // Allow balance to go negative
        )

        if (isForced) {
            ctx.source.sendFeedback({
                primarySpacedText {
                    +success() - "Returned"
                    +formatKristValue(total) + "to the server from"
                    +of(player.name, STYLE.accent) - "."
                }
            }, true)
        } else {
            ctx.source.sendFeedback({
                success() + of("Thank you for returning your welfare rewards!", STYLE.primary)
            }, false)
        }
    }

    private fun getWelfareReturnAmount(profile: GameProfile): MonetaryAmount = transaction(kpdb) {
        val (_, welfareTxs) = findWelfareTransactions(profile)
        MonetaryAmount(welfareTxs.sumOf { it.amount })
    }

    private fun confirmReturnWelfare(uctx: CommandContext<ServerCommandSource>) {
        val player = uctx.source.playerOrThrow
        val returnAmount = getWelfareReturnAmount(player.gameProfile)
        uctx.source.sendFeedback({
            of("Are you sure you want to do this?\n" +
                "This will return ", STYLE.warning) + of("all ", STYLE.accent.plus(Formatting.BOLD)) +
                of("of your welfare rewards to the server (", STYLE.warning) +
                returnAmount.toText(long = true) +
                of("), this includes your starting balance!\n\n", STYLE.warning) +
                of("Click to CONFIRM", *linkFormatting, *STYLE.danger.toTypedArray())
                    .callback(owner = player.uuid, name = "Return Welfare") {
                        returnWelfare(player.gameProfile, it)
                    }
                    .hover(of("LAST CHANCE!!!", STYLE.danger))
        }, false)
    }

    private fun confirmOtherReturnWelfare(uctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(uctx, "player")

        players.forEach { player ->
            if (uctx.source.isExecutedByPlayer) {
                val callbackOwner = uctx.source.player?.uuid
                val returnAmount = getWelfareReturnAmount(player)
                uctx.source.sendFeedback({
                    of("Are you sure you want to do this?\n" +
                        "This will return ", STYLE.warning) + of("all ", STYLE.accent.plus(Formatting.BOLD)) +
                        of("of ${player.name}'s welfare rewards to the server (", STYLE.warning) +
                        returnAmount.toText(long = true) +
                        of("), this includes their starting balance!\n\n", STYLE.warning) +
                        of("Click to CONFIRM", *linkFormatting, *STYLE.danger.toTypedArray())
                            .callback(owner = callbackOwner, name = "Return ${player.name}'s Welfare") {
                                returnWelfare(player, it)
                            }
                            .hover(of("LAST CHANCE!!!", STYLE.danger))
                }, false)
            } else {
                returnWelfare(player, uctx)
            }
        }
    }

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        dispatcher.register(
            literal("welfare")
                .then(literal("opt")
                    .then(literal("out").then(
                        argument("player", GameProfileArgumentType.gameProfile())
                            .requiresPermission(Permission.OPT_OUT_OTHER, 3)
                            .executesAsync(::optOutOther)
                    )
                        .requiresPermission(Permission.OPT_OUT, 0)
                        .executesAsync(::optOut)
                    )
                    .then(literal("in").then(
                        argument("player", GameProfileArgumentType.gameProfile())
                            .requiresPermission(Permission.OPT_IN_OTHER, 3)
                            .executesAsync(::optInOther)
                    )
                        .requiresPermission(Permission.OPT_IN, 0)
                        .executesAsync(::optIn)
                    )
                )
                .then(literal("other")
                    .requiresPermission(Permission.CHECK_OTHER, 3)
                    .then(
                        argument("player", GameProfileArgumentType.gameProfile())
                            .executesAsync(::checkOtherStatus)
                    ))
                .then(literal("return")
                    .requiresPermission(Permission.RETURN, 0)
                    .then(
                        argument("player", GameProfileArgumentType.gameProfile())
                            .requiresPermission(Permission.RETURN_OTHER, 3)
                            .executesAsync(::confirmOtherReturnWelfare)
                    )
                    .executesAsync(::confirmReturnWelfare)
                )
                .requiresPermission(Permission.CHECK, 0)
                .executesAsync(::checkStatus)
        )
    }
}
