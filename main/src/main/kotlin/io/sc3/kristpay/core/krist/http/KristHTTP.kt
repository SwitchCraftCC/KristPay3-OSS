/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import io.sc3.kristpay.core.config.CONFIG

object KristHTTP {
    private val baseUrl: String = "https://${CONFIG.krist.node}"
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    suspend fun request(path: String, builder: HttpRequestBuilder.() -> Unit) = client.request(baseUrl + path, builder)
    suspend inline fun <reified T> HttpResponse.unwrap(): T = okOrThrow(this.body())

    suspend inline fun <reified R> get(path: String, crossinline builder: HttpRequestBuilder.() -> Unit = {})
        = request(path) {
            method = HttpMethod.Get
            builder()
        }.unwrap<R>()

    suspend inline fun <reified R> post(path: String, crossinline builder: HttpRequestBuilder.() -> Unit = {})
        = request(path) {
            method = HttpMethod.Post
            builder()
        }.unwrap<R>()

    suspend inline fun <reified T, reified R> post(path: String, body: T, crossinline builder: HttpRequestBuilder.() -> Unit = {})
        = request(path) {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(serializer(), body))
            builder()
        }.unwrap<R>()

    val ws = WebsocketController(this)

    val lookup = LookupController(this)
    val addresses = AddressesController(this)
    val transactions = TransactionsController(this)
}
