/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.sc3.kristpay.fabric.client.Sounds
import io.sc3.kristpay.fabric.client.config.ClientConfig.config
import io.sc3.kristpay.fabric.client.config.ClientConfig.spec
import io.sc3.text.of
import me.shedaniel.clothconfig2.api.ConfigBuilder

class ModMenu: ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        val builder = ConfigBuilder.create().setParentScreen(parent)
            .setTitle(of("KristPay"))
            .setSavingRunnable {
                spec.correct(config)
                config.save()
            }

        val client = builder.getOrCreateCategory(of("Client"))
        client.addEntry(builder.entryBuilder()
            .startEnumSelector(
                of("Balance HUD Mode"),
                ClientConfig.BalanceHUDMode::class.java,
                config.getEnum("show_balance_hud", ClientConfig.BalanceHUDMode::class.java)
            )
            .setEnumNameProvider { of((it as ClientConfig.BalanceHUDMode).displayName) }
            .setDefaultValue(ClientConfig.BalanceHUDMode.TAB_MENU)
            .setSaveConsumer { config.set("show_balance_hud", it) }
            .build()
        )

        client.addEntry(builder.entryBuilder()
            .startBooleanToggle(of("Pay Animations"), config.get("pay_animations"))
            .setDefaultValue(true)
            .setSaveConsumer { config.set("pay_animations", it) }
            .build()
        )

        client.addEntry(builder.entryBuilder()
            .startBooleanToggle(of("Pay Sounds"), config.get("pay_sounds"))
            .setDefaultValue(true)
            .setSaveConsumer { config.set("pay_sounds", it) }
            .build()
        )

        client.addEntry(builder.entryBuilder()
            .startBooleanToggle(of("Autocomplete /pay from Monitors"), config.get("autocomplete_from_monitors"))
            .setDefaultValue(true)
            .setSaveConsumer { config.set("autocomplete_from_monitors", it) }
            .build()
        )

        client.addEntry(builder.entryBuilder()
            .startBooleanToggle(of("Funny Balance"), config.get("funny_balance"))
            .setDefaultValue(false)
            .setSaveConsumer { config.set("funny_balance", it) }
            .build()
        )

        client.addEntry(builder.entryBuilder()
            .startIntSlider(of("Funny Balance Speed"), config.get("funny_balance_speed"), 1, 100)
            .setDefaultValue(10)
            .setSaveConsumer { config.set("funny_balance_speed", it) }
            .build()
        )

        client.addEntry(SoundEnumListEntry(of("Balance Decrease Sound"), ClientConfig.BalanceDecreaseSound::class.java, config.getEnum("balance_decrease_sound", ClientConfig.BalanceDecreaseSound::class.java),
            defaultValue = { ClientConfig.BalanceDecreaseSound.APPLE_PAY },
            onValueChanged = { Sounds.playSound(it.soundEvent, 50) },
            saveConsumer = { config.set("balance_decrease_sound", it) },
            nameProvider = { of(it.displayName) }
        ))

        builder.build()
    }
}
