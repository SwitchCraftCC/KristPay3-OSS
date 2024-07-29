/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.text

import net.minecraft.text.LiteralTextContent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.text.of
import kotlin.contracts.contract

inline fun buildText(builder: TextBuilder.() -> Unit): MutableText = TextBuilder().apply(builder).build()
inline fun buildSpacedText(builder: TextBuilder.() -> Unit): MutableText = TextBuilder(intercalate = " ").apply(builder).build()
inline fun primarySpacedText(builder: TextBuilder.() -> Unit): MutableText = buildSpacedText { +STYLE.primary; builder() }

private data class TextElement(val text: MutableText, val shouldIntercalate: Boolean = true)

class TextBuilder(private val intercalate: MutableText? = null) {
    constructor(intercalate: String) : this(of(intercalate))

    fun builder(builder: TextBuilder.() -> Unit) = this.apply(builder).build()

    private val parts = mutableListOf<TextElement>()

    private fun MutableList<TextElement>.add(element: MutableText): Boolean = add(TextElement(element))

    fun formatting(formatting: List<Formatting>) = parts.add(of("", formatting)).let { this }
    fun formatting(vararg formatting: Formatting) = parts.add(of("", *formatting)).let { this }

    operator fun Formatting.unaryPlus() = parts.apply {
        add(TextElement(of("", this@unaryPlus), false))
    }.let { this@TextBuilder }
    operator fun List<Formatting>.unaryPlus() = parts.apply {
        add(TextElement(of("", this@unaryPlus), false))
    }.let { this@TextBuilder }
    operator fun String.unaryPlus() = parts.add(of(this@unaryPlus)).let { this@TextBuilder }
    operator fun MutableText.unaryPlus() = parts.add(this@unaryPlus).let { this@TextBuilder }

    operator fun plus(other: TextBuilder) = parts.addAll(other.parts).let { this }
    operator fun plus(other: String) = +other
    operator fun plus(other: MutableText) = +other

    operator fun String.unaryMinus() = parts.add(TextElement(of(this@unaryMinus), false)).let { this@TextBuilder }
    operator fun MutableText.unaryMinus() = parts.add(TextElement(this@unaryMinus, false)).let { this@TextBuilder }
    operator fun minus(other: String) = parts.add(TextElement(of(other), false)).let { this }
    operator fun minus(other: MutableText) = parts.add(TextElement(other, false)).let { this }

    fun build(): MutableText {
        if (parts.isEmpty()) {
            return of("")
        }

        val first = parts.first()
        val rest = parts.drop(1)
        return rest.fold(first.text) { acc, (text, shouldIntercalate) ->
            if (intercalate != null && shouldIntercalate && !acc.last().isBlank()) {
                acc.append(intercalate).append(text)
            } else {
                acc.append(text)
            }
        }
    }

    private fun Text.isBlank() = (this.content as? LiteralTextContent)?.string?.isBlank() ?: false

    private fun Text.last() = this.siblings.lastOrNull() ?: this
}
