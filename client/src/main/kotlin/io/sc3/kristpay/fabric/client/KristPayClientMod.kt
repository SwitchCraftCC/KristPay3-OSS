/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dan200.computercraft.core.terminal.Terminal
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity
import io.sc3.kristpay.fabric.client.config.ClientConfig.config
import io.sc3.kristpay.fabric.client.hud.BalanceHud
import io.sc3.kristpay.fabric.client.hud.KristSnow
import io.sc3.kristpay.fabric.client.hud.WarningBar
import io.sc3.kristpay.fabric.client.mixins.ChatHudAccessor
import io.sc3.kristpay.fabric.shared.argument.PayableArgumentType
import io.sc3.kristpay.fabric.shared.packet.AmbiguityResponseS2CPacket
import io.sc3.kristpay.fabric.shared.packet.BalanceUpdateS2CPacket
import io.sc3.kristpay.fabric.shared.packet.registerClientReceiver
import io.sc3.text.of
import io.sc3.text.plus
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.command.CommandSource
import net.minecraft.text.MutableText
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import java.util.concurrent.CompletableFuture

class KristPayClientMod : ClientModInitializer {
    override fun onInitializeClient() {
        PayableArgumentType.extraProviders.add { _, builder ->
            if (config.get("autocomplete_from_monitors")) {
                client.execute {
                    getSuggestionsFromVisibleTextSource(client.player ?: return@execute, builder)
                }
            }

            builder.buildFuture()
        }

        PayableArgumentType.extraProviders.add { _, builder ->
            client.execute {
                CommandSource.suggestMatching(findAddresses(
                    (client.inGameHud.chatHud as ChatHudAccessor).messages.map { it.content.string }
                ), builder)
            }

            builder.buildFuture()
        }

        registerClientReceiver(BalanceUpdateS2CPacket.id, BalanceUpdateS2CPacket::fromBytes)
        registerClientReceiver(AmbiguityResponseS2CPacket.id, AmbiguityResponseS2CPacket::fromBytes)

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            client.execute {
                BalanceHud.balance = null
                BalanceHud.balancePrevious = null
                PayableArgumentType.userCache.clear()
            }
        }

        BalanceUpdateS2CPacket.EVENT.register {
            client.execute {
                BalanceHud.balancePrevious = BalanceHud.balance
                BalanceHud.balance = it.balance

                if (BalanceHud.balancePrevious == null) {
                    BalanceHud.balancePrevious = it.balance
                } else if (BalanceHud.balancePrevious != BalanceHud.balance) {
                    BalanceHud.playSound(it.balance - BalanceHud.balancePrevious!!)
                    BalanceHud.resetAnimation()
                    BalanceHud.snow.add(KristSnow(it.balance - BalanceHud.balancePrevious!!))
                }
            }
        }

        AmbiguityResponseS2CPacket.EVENT.register {
            client.execute {
                PayableArgumentType.userCache[it.address] = it.exists
                WarningBar.refresh(WarningBar.currentParse.get())
            }
        }

        Sounds.init()
    }

    private fun getSuggestionsFromVisibleTextSource(
        player: ClientPlayerEntity,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions>? {
        val start: Vec3d = player.eyePos
        val end: Vec3d = start.add(
            player.rotationVector.multiply(10.0)
        )

        val result: BlockHitResult = player.world.raycast(
            RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
            )
        )

        if (result.type == HitResult.Type.BLOCK) {
            when (val entity = player.world.getBlockEntity(result.blockPos)) {
                is MonitorBlockEntity -> entity.originClientMonitor?.terminal?.let { term ->
                    return CommandSource.suggestMatching(findAddresses(term), builder)
                }
                is SignBlockEntity -> return CommandSource.suggestMatching(findAddresses(entity), builder)
            }
        }

        return null
    }

    private val nameRegex = Regex("\\b(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kst\\b", RegexOption.IGNORE_CASE)
    private val addressRegex = Regex("\\bk[a-z0-9]{9}\\b")
    private val minecraftUsernameRegex = Regex("\\b\\w{3,16}\\b")
    private fun findAddresses(text: String): List<String> {
        val addresses = addressRegex.findAll(text)
        val names = nameRegex.findAll(text)
        return addresses.map { it.value }.toList() + names.map { it.value }.toList()
    }

    private fun findAddresses(term: Terminal): List<String> {
        return (0 until term.height).flatMap { line ->
            val text = term.getLine(line)
            findAddresses(text.toString())
        }
    }

    private fun findAddresses(sign: SignBlockEntity): List<String> =
        sign.lines.values.flatMap(::findAddresses)

    private val SignBlockEntity.lines: Map<Int, String>
        get() = (1 .. 8).associateWith { getText(it <= 4).getMessage((it - 1) % 4, true).string }

    private fun findAddresses(strs: List<String>): List<String> = strs.flatMap(::findAddresses)

    private fun removeAllStyle(text: MutableText): MutableText {
        return text.withoutStyle().map{ it.copyContentOnly() }.fold(of(""), MutableText::plus)
    }

    companion object {
        val MOD_ID = "kristpay-client"
    }
}
