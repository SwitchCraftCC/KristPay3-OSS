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
import io.sc3.kristpay.api.UserID
import io.sc3.kristpay.api.WalletID
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.KristPayUnallocated
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.fabric.extensions.toText
import io.sc3.kristpay.fabric.text.primarySpacedText
import io.sc3.text.formatKristValue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import java.util.*
import kotlin.math.absoluteValue

@KristPayCommand
object BalanceCommand : KristPayConsumer(), CommandObject {
    object Permission {
        private const val ROOT = "kristpay.balance"
        const val GET       = "$ROOT.get.base"
        const val GET_OTHER = "$ROOT.get.others"
        const val SET       = "$ROOT.set.base"
        const val SET_OTHER = "$ROOT.set.others"
    }

    private fun runAnonymous(ctx: CommandContext<ServerCommandSource>) {
        val balance = getBalanceOfPlayer(ctx.source.playerOrThrow.uuid)
        ctx.source.sendFeedback({ of("Balance: ", STYLE.primary).append(balance.toText()) }, false)
    }

    private fun runWithPlayer(ctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(ctx, "player")

        players.forEach {
            val balance = getBalanceOfPlayer(it.id)
            ctx.source.sendFeedback({
                of(it.name, STYLE.accent) + of("'s balance: ", STYLE.primary).append(balance.toText())
            }, false)
        }
    }

    private fun runWithWallet(ctx: CommandContext<ServerCommandSource>) {
        var walletStr = StringArgumentType.getString(ctx, "walletID")
        val balance = try {
            getSnapshot(UUID.fromString(walletStr))
        } catch (e: java.lang.IllegalArgumentException) {
            getSnapshot(walletStr)
        }?.also { walletStr = it.name }?.balance

        if (balance != null) {
            ctx.source.sendFeedback({
                of("Wallet '", STYLE.primary) + of(walletStr, STYLE.accent) + of("'s balance: ").append(balance.toText())
            }, false)
        } else {
            ctx.source.sendFeedback({
                of("Wallet '", STYLE.primary) + of(walletStr, STYLE.accent) + of("' does not exist!")
            }, false)
        }
    }

    private fun setBalance(ctx: CommandContext<ServerCommandSource>, wallet: UUID, amount: Int): MonetaryAmount {
        val snapshot = getSnapshot(wallet)

        if (snapshot != null) {
            val delta = amount - snapshot.balance.amount
            val isIncrease = delta > 0

            API.initializeTransaction(
                from = if (isIncrease) KristPayUnallocated else KristPayWallet(wallet),
                to = if (isIncrease) KristPayWallet(wallet) else KristPayUnallocated,
                amount = MonetaryAmount(delta.absoluteValue),
                initiator = ctx.source.player?.let { Initiator.User(it.uuid) } ?: Initiator.Server,
                systemMetadata = buildJsonObject {
                    put("command", "setbal")
                    put("fake", true)
                    put("new_balance", amount)
                    put("old_balance", snapshot.balance.amount)
                },
                sendNotification = true,
                allowDebt = true
            )

            return snapshot.balance
        } else {
            throw IllegalArgumentException("Wallet does not exist!")
        }
    }

    private fun setWithPlayer(ctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(ctx, "player")
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        players.forEach {
            val wallet = API.getDefaultWallet(it.id)
                ?: return@forEach ctx.source.sendFeedback({
                    of("Player ", STYLE.primary) + of(it.name, STYLE.accent) + of(" does not have a wallet!")
                }, false)

            val originalBalance = setBalance(ctx, wallet, amount)

            ctx.source.sendFeedback({
                primarySpacedText {
                    + "Set" + of(it.name, STYLE.accent) - "'s balance to" + formatKristValue(
                        amount,
                        formatting = STYLE.accent
                    )
                    +"(was" + formatKristValue(originalBalance.amount, formatting = STYLE.accent) - ")"
                }
            }, true)
        }
    }

    private fun setAnonymous(ctx: CommandContext<ServerCommandSource>) {
        val player = ctx.source.playerOrThrow.uuid
        val wallet = API.getDefaultWallet(player)!!
        val amount = IntegerArgumentType.getInteger(ctx, "amount")
        val originalBalance = setBalance(ctx, wallet, amount)

        ctx.source.sendFeedback({
            primarySpacedText {
                +"Set your balance to" + formatKristValue(amount, formatting = STYLE.accent)
                +"(was" + formatKristValue(originalBalance.amount, formatting = STYLE.accent) - ")"
            }
        }, true )
    }

    private fun setWithWallet(ctx: CommandContext<ServerCommandSource>) {
        var walletStr = StringArgumentType.getString(ctx, "walletID")
        val amount = IntegerArgumentType.getInteger(ctx, "amount")
        val snapshot = try {
            getSnapshot(UUID.fromString(walletStr))
        } catch (e: java.lang.IllegalArgumentException) {
            getSnapshot(walletStr)
        }?.also { walletStr = it.name }

        if (snapshot != null) {
            val originalBalance = setBalance(ctx, snapshot.id, amount)

            ctx.source.sendFeedback({
                primarySpacedText {
                    +"Set wallet '" - of(walletStr, STYLE.accent) - "'s balance to"
                    +formatKristValue(amount) + "(was" + formatKristValue(originalBalance.amount) - ")"
                }
            }, true)
        } else {
            ctx.source.sendFeedback(
                { of("Wallet '", STYLE.primary) + of(walletStr, STYLE.accent) + of("' does not exist!") },
                false
            )
        }
    }

    private fun getSnapshot(target: WalletID) = API.getWalletSnapshot(target)
    private fun getSnapshot(name: String) = API.findWalletByName(name)
    private fun getBalanceOfPlayer(target: UserID) = API.getDefaultWalletSnapshot(target)?.balance ?: MonetaryAmount.ZERO

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        val aliases = listOf("balance", "bal")
        val walletAliases = listOf("wallet", "w")

        aliases.forEach { alias ->
            dispatcher.register(
                literal(alias)
                    .requiresPermission(Permission.GET, 0)
                    .executesAsync(::runAnonymous)
                    .then(
                        argument("player", GameProfileArgumentType.gameProfile())
                                .requiresPermission(Permission.GET_OTHER, 3)
                                .executesAsync(::runWithPlayer)
                    )
            )

            walletAliases.forEach { walletAlias ->
                dispatcher.register(
                    literal("$alias:$walletAlias")
                        .requiresPermission(Permission.GET_OTHER, 3)
                        .then(argument("walletID", StringArgumentType.word())
                            .executesAsync(::runWithWallet)
                        )
                )
            }
        }

        dispatcher.register(
            literal("setbal")
                .requiresPermission(Permission.SET, 3)
                .then(
                    argument("amount", IntegerArgumentType.integer())
                        .executesAsync(::setAnonymous)
                )
                .then(
                    argument("player", GameProfileArgumentType.gameProfile())
                        .requiresPermission(Permission.SET_OTHER, 3)
                        .then(argument("amount", IntegerArgumentType.integer())
                            .executesAsync(::setWithPlayer)
                    )
                )
        )

        walletAliases.forEach { walletAlias ->
            dispatcher.register(
                literal("setbal:$walletAlias")
                    .requiresPermission(Permission.SET_OTHER, 3)
                    .then(argument("walletID", StringArgumentType.word()).then(
                        argument("amount", IntegerArgumentType.integer())
                            .executesAsync(::setWithWallet)
                    )
                )
            )
        }
    }
}
