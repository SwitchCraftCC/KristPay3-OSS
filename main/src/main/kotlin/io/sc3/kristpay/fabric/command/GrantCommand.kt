/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.KristPayUnallocated
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.fabric.text.primarySpacedText
import io.sc3.text.formatKristValue
import io.sc3.text.success
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import java.util.*
import kotlin.math.absoluteValue

@KristPayCommand
object GrantCommand : KristPayConsumer(), CommandObject {
    object Permission {
        private const val ROOT = "kristpay.grant"
        const val GRANT        = "$ROOT.base"
        const val GRANT_OTHER  = "$ROOT.others"
    }

    private fun grant(ctx: CommandContext<ServerCommandSource>, wallet: UUID, amount: Int) {
        val isIncrease = amount > 0
        API.initializeTransaction(
            from = if (isIncrease) KristPayUnallocated else KristPayWallet(wallet),
            to = if (isIncrease) KristPayWallet(wallet) else KristPayUnallocated,
            amount = MonetaryAmount(amount.absoluteValue),
            initiator = ctx.source.player?.let { Initiator.User(it.uuid) } ?: Initiator.Server,
            systemMetadata = buildJsonObject {
                put("command", "grant")
                put("fake", true)
            },
            sendNotification = true,
            allowDebt = true
        )
    }

    private fun grantAnonymous(ctx: CommandContext<ServerCommandSource>) {
        val player = ctx.source.playerOrThrow.uuid
        val wallet = API.getDefaultWallet(player)!!
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        if (amount == 0) return ctx.source.sendFeedback({
            primarySpacedText {
                +STYLE.error - "You can't grant 0 KST!"
            }
        }, true)

        grant(ctx, wallet, amount)

        if (amount > 0) {
            ctx.source.sendFeedback({
                primarySpacedText {
                    +success() - "Granted" + formatKristValue(
                        amount.absoluteValue,
                        formatting = STYLE.accent
                    ) + "to your wallet"
                }
            }, true)
        } else {
            ctx.source.sendFeedback({
                primarySpacedText {
                    +success() - "Deducted" + formatKristValue(
                        amount.absoluteValue,
                        formatting = STYLE.accent
                    ) + "from your wallet"
                }
            }, true)
        }
    }

    private fun grantWithPlayer(ctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(ctx, "player")
        val amount = IntegerArgumentType.getInteger(ctx, "amount")

        players.forEach {
            val wallet = API.getDefaultWallet(it.id)
                ?: return@forEach ctx.source.sendFeedback(
                    { of("Player ", STYLE.primary) + of(it.name, STYLE.accent) + of(" does not have a wallet!") },
                    false
                )

            grant(ctx, wallet, amount)

            val targetText = ctx.source.server.playerManager.getPlayer(it.id)?.displayName?.copy()
                ?: of(it.name, STYLE.accent)

            if (amount > 0) {
                ctx.source.sendFeedback({
                    primarySpacedText {
                        +success() - "Granted" + formatKristValue(
                            amount.absoluteValue,
                            formatting = STYLE.accent
                        ) + "to" + targetText
                    }
                }, true)
            } else {
                ctx.source.sendFeedback({
                    primarySpacedText {
                        +success() - "Deducted" + formatKristValue(
                            amount.absoluteValue,
                            formatting = STYLE.accent
                        ) + "from" + targetText
                    }
                }, true)
            }
        }
    }

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        dispatcher.register(
            CommandManager.literal("grant")
                .requiresPermission(Permission.GRANT, 3)
                .then(
                    CommandManager.argument("amount", IntegerArgumentType.integer())
                        .executesAsync(::grantAnonymous)
                )
                .then(
                    CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                        .requiresPermission(Permission.GRANT_OTHER, 3)
                        .then(
                            CommandManager.argument("amount", IntegerArgumentType.integer())
                                .executesAsync(::grantWithPlayer)
                        )
                )
        )
    }
}
