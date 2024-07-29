/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.hud

import io.sc3.kristpay.fabric.client.Sounds
import io.sc3.kristpay.fabric.client.config.ClientConfig
import io.sc3.kristpay.fabric.client.config.ClientConfig.config
import io.sc3.text.formatKristValue
import io.sc3.text.of
import io.sc3.text.plus
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Formatting.*
import net.minecraft.util.math.RotationAxis
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

data class KristSnow(val amount: Int, var life: Float = 80f)

object BalanceHud {
    var balance: Int? = null
    var balancePrevious: Int? = null

    val snow = mutableListOf<KristSnow>()

    var animationLeft = 0f
    var displayLeft = 0f
    fun resetAnimation() {
        animationLeft = 100f
        displayLeft = 160f
    }

    var lol = 0f
    const val velocity = 1f
    object DVD {
        var x = 0f
        var y = 0f
        var vx = velocity
        var vy = velocity
    }

    @JvmStatic
    fun shouldForceRender() = config.get("pay_animations") && displayLeft > 0f

    @JvmStatic
    fun render(
        client: MinecraftClient,
        ctx: DrawContext,
        delta: Float
    ) {
        if (balance == null || balancePrevious == null) return

        val matrices = ctx.matrices
        matrices.push()

        val funnySpeed = config.getOrElse("funny_balance_speed", 10).toFloat() / 10f

        lol += 0.025f * delta * funnySpeed

        if (animationLeft > 0f) {
            animationLeft -= delta
        }

        if (displayLeft > 0f) {
            displayLeft -= delta
        }

        for (snow in snow) {
            snow.life -= delta
        }
        snow.removeIf { it.life <= 0f }

        val text = if (config.get("pay_animations") && animationLeft > 0f) {
            val showValue = ease(1f - animationLeft / 100f) * (balance!! - balancePrevious!!) + balancePrevious!!
            of("Balance: ", DARK_GREEN) + formatKristValue(showValue.roundToInt(), long = false, ITALIC, GREEN)
        } else {
            of("Balance: ", DARK_GREEN) + formatKristValue(balance!!, long = false, ITALIC, GREEN)
        }

        val tr = client.textRenderer
        val width = tr.getWidth(text)
        val balanceWidth = tr.getWidth("Balance: ")
        val plusWidth = tr.getWidth("+")

        if (config.get("funny_balance")) {
            DVD.x += (DVD.vx * delta * funnySpeed)
            DVD.y += (DVD.vy * delta * funnySpeed)

            if (DVD.x > client.window.scaledWidth) {DVD.vx = -velocity; DVD.x = client.window.scaledWidth.toFloat()}
            if (DVD.x < 0) {DVD.vx = velocity; DVD.x = 0f}
            if (DVD.y > client.window.scaledHeight) {DVD.vy = -velocity; DVD.y = client.window.scaledHeight.toFloat()}
            if (DVD.y < 0) {DVD.vy = velocity; DVD.y = 0f}

            matrices.translate(DVD.x.toDouble(), DVD.y.toDouble(), 0.0)
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(lol))

            ctx.drawTextWithShadow(
                tr,
                text,
                (-width.toFloat() / 2).toInt(),
                (-tr.fontHeight.toFloat() / 2).toInt(),
                -1
            )
        } else {
            ctx.drawTextWithShadow(
                tr,
                text,
                10,
                10,
                -1
            )
        }

        if (config.get("pay_animations")) {
            for (snow in snow) {
                val (xoff, yoff) = if (config.get("funny_balance")) {
                    -width.toFloat() / 2 to
                    -tr.fontHeight.toFloat() / 2
                } else {
                    10f to 10f
                }

                val snowText = if (snow.amount > 0f) of("+", DARK_GREEN) + formatKristValue(snow.amount.absoluteValue, long = false, ITALIC, GREEN)
                    else of("-", DARK_RED) + formatKristValue(snow.amount.absoluteValue, long = false, ITALIC, RED)
                ctx.drawTextWithShadow(
                    tr,
                    snowText,
                    (xoff + balanceWidth - plusWidth).toInt(),
                    (yoff + tr.fontHeight * (1.1f + 2.4f*(1f - snow.life / 80f))).toInt(),
                    (snow.life / 80f * 0xFF).toInt().coerceAtLeast(4).shl(24).or(0xFFFFFF) // ARGB
                )
            }
        }

        matrices.pop()
    }

    private fun ease(t: Float) = (t - 1) * (t - 1) * (t - 1) + 1

    fun playSound(delta: Int) {
        if (config.get("pay_sounds")) {
            when {
                delta < 0 -> config.getEnum(
                    "balance_decrease_sound",
                    ClientConfig.BalanceDecreaseSound::class.java
                )?.soundEvent?.let { Sounds.playSound(it, 50) }
                delta == 0 -> {}
                delta < 50 -> Sounds.playSound(Sounds.COINS_TINY, 50)
                delta < 200 -> Sounds.playSound(Sounds.COINS_MEDIUM, 50)
                else -> Sounds.playSound(Sounds.COINS_LARGE, 50)
            }
        }
    }
}
