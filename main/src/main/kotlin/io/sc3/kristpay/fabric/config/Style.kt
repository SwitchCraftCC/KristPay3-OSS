/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

@file:UseSerializers(FormattingSerializer::class)

package io.sc3.kristpay.fabric.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.util.Formatting
import io.sc3.kristpay.core.config.CONFIG

@Serializable
data class Style(
    val primary: List<Formatting> = listOf(Formatting.GREEN),
    val secondary: List<Formatting> = listOf(Formatting.DARK_GREEN),
    val accent: List<Formatting> = listOf(Formatting.YELLOW),
    val success: List<Formatting> = listOf(Formatting.GREEN),
    val error: List<Formatting> = listOf(Formatting.RED),
    val cancel: List<Formatting> = listOf(Formatting.RED),
    val danger: List<Formatting> = listOf(Formatting.RED, Formatting.BOLD),
    val warning: List<Formatting> = listOf(Formatting.GOLD),
    val note: List<Formatting> = listOf(Formatting.ITALIC, Formatting.GRAY),
    val noteHeader: List<Formatting> = listOf(Formatting.ITALIC, Formatting.DARK_GRAY),
    val address: List<Formatting> = listOf(Formatting.AQUA),
    val confirm: List<Formatting> = listOf(Formatting.AQUA),
)

object FormattingSerializer : KSerializer<Formatting> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Format", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Formatting) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Formatting = Formatting.byName(decoder.decodeString())!!
}

val STYLE = CONFIG.frontend.style
