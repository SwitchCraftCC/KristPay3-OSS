/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.http

import kotlinx.serialization.Serializable

class WebsocketController(private val client: KristHTTP) {
    @Serializable data class StartRequest(val privatekey: String)
    @Serializable data class StartResponse(val url: String)
    suspend fun start() = client.post<StartResponse>("$ROOT/start")
    suspend fun start(privateKey: String) = client.post<StartRequest, StartResponse>("$ROOT/start", StartRequest(privateKey))

    companion object {
        private const val ROOT = "/ws"
    }
}
