/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.hud

import com.mojang.brigadier.ParseResults
import io.sc3.kristpay.fabric.client.client
import io.sc3.kristpay.fabric.shared.argument.PayableArgumentType
import io.sc3.kristpay.fabric.shared.argument.PayableArgumentType.Companion.userCache
import io.sc3.kristpay.fabric.shared.packet.CheckAmbiguityC2SPacket
import io.sc3.text.of
import io.sc3.text.plus
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.command.CommandSource
import net.minecraft.util.Formatting.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object WarningBar {
    private var showWarning = AtomicBoolean(false)
    var currentParse = AtomicReference<ParseResults<CommandSource>?>(null)


    fun refresh(parse: ParseResults<CommandSource>?) {
        showWarning.set(false)
        currentParse.set(parse)

        if (parse == null) return
        showWarning.compareAndSet(false, parse.context.arguments
            .mapNotNull { it.value.result as? PayableArgumentType.AddressOrPlayer }
            .any { checkArgument(parse.context.source, it.target) })
    }

    private fun checkArgument(
        source: CommandSource,
        text: String
    ): Boolean {
        if (PayableArgumentType.addressRegex.matchEntire(text) != null) {
            if (source.playerNames.contains(text)) {
                userCache[text] = true
                return true
            } else {
                // First check if we already have a cached value
                userCache[text]?.let { return it }

                // Otherwise launch a request to the server
                MinecraftClient.getInstance().networkHandler?.sendPacket(
                    CheckAmbiguityC2SPacket(text).build()
                )

                userCache[text] = false // Assume false until we get a response
            }
        }

        return false
    }

    fun render(screen: ChatScreen, ctx: DrawContext) {
        if (!showWarning.get()) return

        val text = of("", YELLOW) + of("Warning!", GOLD, BOLD) + " The target you specified is both a valid user and a " +
                "valid Krist address. This command prefers users by default. If you wish to transfer to the address " +
                "instead, prefix the argument with " + of("address:", GREEN) + " instead."

        val tr = client.textRenderer
        val lineHeight = tr.fontHeight + 1
        val wrappedText = tr.wrapLines(text, screen.width - 4)

        val bgColor = client.options.getTextBackgroundColor(Integer.MIN_VALUE)
        ctx.fill(0, 7, screen.width, 10 + lineHeight * wrappedText.size + 2, bgColor)

        val maxWidth = wrappedText.maxOf { tr.getWidth(it) }
        val centeredX = (screen.width - maxWidth) / 2

        for (i in wrappedText.indices) {
            ctx.drawTextWithShadow(tr, wrappedText[i], centeredX, 10 + i * lineHeight, 0xFFFFFFFF.toInt())
        }
    }
}
