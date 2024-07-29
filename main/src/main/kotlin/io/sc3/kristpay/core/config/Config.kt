/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.config

import com.akuleshov7.ktoml.TomlConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.fabricmc.loader.api.FabricLoader
import io.sc3.kristpay.core.krist.makeV2Address
import io.sc3.kristpay.fabric.config.FrontendConfig
import io.sc3.kristpay.fabric.config.WelfareConfig
import kotlin.io.path.pathString

@Serializable
data class KristPayConfig internal constructor(
    val jdbc: JDBCConfig,
    val krist: KristConfig,
    val prometheus: PrometheusConfig = PrometheusConfig(),
    val alerting: AlertingConfig = AlertingConfig(),

    val frontend: FrontendConfig = FrontendConfig(),
    val welfare: WelfareConfig = WelfareConfig()
) {

    @Serializable
    data class JDBCConfig internal constructor(
        val url: String,
        val user: String,
        val password: String
    )

    @Serializable
    data class KristConfig internal constructor(
        val node: String = "krist.dev",
        val privateKey: String,
        val advertisedName: String = "switchcraft",

        val refundRatelimit: Int = 5, // seconds; limit for successive refunds for unknown recipients, used to prevent tx loops
    ) {
        val address by lazy { makeV2Address(privateKey) }
    }

    @Serializable
    data class PrometheusConfig internal constructor(
        val port: Int = 9460
    )

    @Serializable
    data class AlertingConfig internal constructor(
        val discordWebhook: String? = null,
        val discordWebhookUsername: String = "KristPay"
    )

}

private val CONFIG_PATH: String = System.getProperty("kp.config") ?: // "run/config/kristpay.toml"
    FabricLoader.getInstance().configDir.resolve("kristpay.toml").pathString

val CONFIG = TomlFileReader(TomlConfig(ignoreUnknownNames = true)).decodeFromFile<KristPayConfig>(serializer(), CONFIG_PATH)
