/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.ws

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

val idCounter = AtomicInteger(0)

@Serializable
abstract class KWSRequest(
    val type: String
) {
    val id: Int = idCounter.incrementAndGet()
}

suspend inline fun <reified T: KWSRequest> T.send()
    = WebsocketManager.messageChannel.send(
        Json.encodeToString(KWSRequest.serializer(), this)
    )
