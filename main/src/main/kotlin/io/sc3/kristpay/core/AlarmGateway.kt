/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.util.supervised
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

val AlarmScope = CoroutineScope(Dispatchers.IO.supervised())

object AlarmGateway {
    private val config by CONFIG::alerting
    val client = HttpClient(CIO)

    fun sendAlert(title: String, description: String) {
        AlarmScope.launch {
            config.discordWebhook?.let { webhook ->
                client.post(webhook) {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(serializer(), buildJsonObject {
                        put("username", config.discordWebhookUsername)
                        put("avatar_url", "https://krist.dev/favicon-128x128.png")

                        put("embeds", buildJsonArray {
                            add(buildJsonObject {
                                put("title", title)
                                put("description", description)
                                put("color", 0xFF0000)
                            })
                        })
                    }))
                }
            }
        }
    }
}
