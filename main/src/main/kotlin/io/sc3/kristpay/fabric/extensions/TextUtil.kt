/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.extensions

import net.minecraft.text.ClickEvent
import net.minecraft.text.ClickEvent.Action.COPY_TO_CLIPBOARD
import net.minecraft.text.ClickEvent.Action.OPEN_URL
import net.minecraft.text.ClickEvent.Action.RUN_COMMAND
import net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND
import net.minecraft.text.HoverEvent
import net.minecraft.text.HoverEvent.Action.SHOW_TEXT
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import io.sc3.kristpay.fabric.config.STYLE


fun of(text: String?, vararg formatting: Formatting): MutableText
        = Text.literal(text ?: "").formatted(*formatting)

fun of(text: String?, formatting: List<Formatting>): MutableText = of(text, *formatting.toTypedArray())

operator fun MutableText.plus(other: MutableText): MutableText = this.append(other)

fun MutableText.hoverText(hover: Text?): MutableText
        = styled { it.withHoverEvent(if (hover != null) HoverEvent(SHOW_TEXT, hover) else null) }

fun MutableText.openUrl(url: String?): MutableText
        = styled { it.withClickEvent(if (url != null) ClickEvent(OPEN_URL, url) else null) }

fun MutableText.runCommand(cmd: String?): MutableText
        = styled { it.withClickEvent(if (cmd != null) ClickEvent(RUN_COMMAND, cmd) else null) }

fun MutableText.suggestCommand(cmd: String?): MutableText
        = styled { it.withClickEvent(if (cmd != null) ClickEvent(SUGGEST_COMMAND, cmd) else null) }

fun MutableText.copyToClipboard(text: String?): MutableText
        = styled { it.withClickEvent(if (text != null) ClickEvent(COPY_TO_CLIPBOARD, text) else null) }

fun MutableText.shiftInsertText(text: String?): MutableText
        = styled { it.withInsertion(text) }

fun MutableText.color(color: Int): MutableText
        = styled { it.withColor(color) }

fun note() = of("\nNOTE: ", STYLE.noteHeader)
