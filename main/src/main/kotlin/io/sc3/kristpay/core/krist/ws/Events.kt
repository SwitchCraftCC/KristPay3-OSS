/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.ws

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import mu.KLogging
import io.sc3.kristpay.core.krist.http.APITransaction
import io.sc3.kristpay.fabric.extensions.getString

abstract class KristEvent<T> {
    private val subscribers = mutableListOf<(T) -> Unit>()

    fun subscribe(subscriber: (T) -> Unit) {
        subscribers.add(subscriber)
    }

    fun emit(data: T) {
        subscribers.forEach { it(data) }
    }
}

object TransactionEvent : KristEvent<APITransaction>()

object EventManager: KLogging() {
    fun fireEvents(message: String) {
        val obj = Json.decodeFromString<JsonObject>(serializer(), message)
        if (obj.getString("type") == "event") {
            when (obj.getString("event")) {
                "transaction" -> TransactionEvent.emit(Json.decodeFromJsonElement(serializer(), obj["transaction"]!!))
                "block" -> {} // We don't care about blocks
                "name" -> {} // We don't care about names
                else -> logger.warn("Unknown event type: ${obj.getString("event")}")
            }
        }
    }
}
