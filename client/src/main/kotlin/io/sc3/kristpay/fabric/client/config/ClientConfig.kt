/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.config

import com.electronwill.nightconfig.core.ConfigSpec
import com.electronwill.nightconfig.core.EnumGetMethod
import com.electronwill.nightconfig.core.file.CommentedFileConfig
import io.sc3.kristpay.fabric.client.Sounds
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.sound.SoundEvent
import java.util.*

object ClientConfig {
    internal val spec = ConfigSpec()
    init {
        spec.defineRestrictedEnum("show_balance_hud", BalanceHUDMode.TAB_MENU, EnumSet.allOf(BalanceHUDMode::class.java), EnumGetMethod.NAME_IGNORECASE)
        spec.define("pay_animations", true)
        spec.define("pay_sounds", true)
        spec.defineRestrictedEnum("balance_decrease_sound", BalanceDecreaseSound.APPLE_PAY, EnumSet.allOf(BalanceDecreaseSound::class.java), EnumGetMethod.NAME_IGNORECASE)

        spec.define("autocomplete_from_monitors", true)

        spec.define("funny_balance", false)
        spec.defineInRange("funny_balance_speed", 10, 1, 100)
    }

    internal val config by lazy {
        val dir = FabricLoader.getInstance().configDir.resolve("kristpay")
        dir.toFile().mkdirs()

        val conf = CommentedFileConfig
            .builder(dir.resolve("client.toml"))
            .autosave()
            .build()

        conf.load()
        spec.correct(conf)
        conf.save()

        conf
    }

    enum class BalanceHUDMode(val displayName: String) {
        ALWAYS("Always"),
        TAB_MENU("Tab Menu"),
        NEVER("Never")
    }

    enum class BalanceDecreaseSound(val displayName: String, val soundEvent: SoundEvent) {
        APPLE_PAY("Apple Pay", Sounds.APPLE_PAY),
        GOOGLE_PAY("Google Pay", Sounds.GOOGLE_PAY),
        ESHOP_PURCHASED("Nintendo eShop (P)", Sounds.ESHOP_PURCHASED),
        ESHOP_TRIAL("Nintendo eShop (T)", Sounds.ESHOP_TRIAL),
        ESHOP_WISH("Nintendo eShop (W)", Sounds.ESHOP_WISH),
        ESHOP_START("Nintendo eShop (S)", Sounds.ESHOP_START)
    }
}
