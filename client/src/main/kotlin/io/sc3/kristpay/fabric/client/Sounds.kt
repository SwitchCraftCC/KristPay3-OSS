/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client

import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.registry.Registries.SOUND_EVENT
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import io.sc3.kristpay.fabric.client.KristPayClientMod.Companion.MOD_ID

object Sounds {
    val COINS_TINY      = SoundEvent.of(Identifier(MOD_ID, "coins_tiny"))
    val COINS_MEDIUM    = SoundEvent.of(Identifier(MOD_ID, "coins_medium"))
    val COINS_LARGE     = SoundEvent.of(Identifier(MOD_ID, "coins_large"))
    val APPLE_PAY       = SoundEvent.of(Identifier(MOD_ID, "apple_pay"))
    val GOOGLE_PAY      = SoundEvent.of(Identifier(MOD_ID, "google_pay"))
    val ESHOP_PURCHASED = SoundEvent.of(Identifier(MOD_ID, "eshop_purchased"))
    val ESHOP_START     = SoundEvent.of(Identifier(MOD_ID, "eshop_start"))
    val ESHOP_TRIAL     = SoundEvent.of(Identifier(MOD_ID, "eshop_trial"))
    val ESHOP_WISH      = SoundEvent.of(Identifier(MOD_ID, "eshop_wish"))

    internal fun init() {
        registerSound(COINS_TINY)
        registerSound(COINS_MEDIUM)
        registerSound(COINS_LARGE)
        registerSound(APPLE_PAY)
        registerSound(GOOGLE_PAY)
        registerSound(ESHOP_PURCHASED)
        registerSound(ESHOP_START)
        registerSound(ESHOP_TRIAL)
        registerSound(ESHOP_WISH)
    }

    private fun registerSound(sound: SoundEvent) {
        Registry.register(SOUND_EVENT, sound.id, sound)
    }

    internal fun playSound(sound: SoundEvent, volume: Int) {
        val vol = volume.toFloat() / 100.0f

        val instance = PositionedSoundInstance.master(sound, 1.0f, vol)
        MinecraftClient.getInstance().soundManager.play(instance)
    }
}
