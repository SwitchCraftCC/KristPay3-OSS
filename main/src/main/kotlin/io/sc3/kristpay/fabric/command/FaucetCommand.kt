/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.events.welfare.FaucetWelfare
import io.sc3.kristpay.fabric.events.welfare.FaucetWelfare.ClaimResult
import io.sc3.kristpay.fabric.events.welfare.WelfareHandlers
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.fabric.extensions.toText
import io.sc3.kristpay.util.toSimpleString
import io.sc3.text.success
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource

@KristPayCommand
object FaucetCommand : KristPayConsumer(), CommandObject {
    private val config by CONFIG.welfare::faucetBonus

    private fun run(ctx: CommandContext<ServerCommandSource>) {
        when (val result = FaucetWelfare.handleClaimFaucet(ctx.source.playerOrThrow.uuid)) {
            is ClaimResult.Success -> {
                ctx.source.sendFeedback({
                    success() + of("Your faucet reward has been redeemed!", STYLE.primary) +
                        of("\nYou can next redeem in: ", STYLE.primary) + of(
                        config.periodDuration.toSimpleString(),
                        STYLE.accent
                    ) + of("\nNext reward: ", STYLE.primary) + result.nextReward.toText()
                }, false)
            }

            is ClaimResult.AlreadyClaimed -> {
                ctx.source.sendFeedback({
                    of("You cannot claim a faucet reward again for another ", STYLE.error) +
                        of(result.remainingTime.toSimpleString(), STYLE.accent)
                }, false)
            }

            is ClaimResult.WelfareDisabled -> {
                ctx.source.sendFeedback({
                    of("You are opted out of welfare, and are therefore ineligible for faucet rewards.", STYLE.error)
                }, true)
            }

            ClaimResult.RequiredActiveTimeNotMet -> {
                ctx.source.sendFeedback({
                    of("You cannot claim a faucet reward yet today. Keep playing and try again later!", STYLE.error)
                }, false)
            }
        }
    }

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        dispatcher.register(
            literal("faucet")
                .requiresPermission(WelfareHandlers.Permission.CLAIM_FAUCET, 0)
                .executesAsync(::run)
        )
    }
}
