/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.http

import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

class LookupController(private val client: KristHTTP) {
    @Serializable data class TransactionResponse(
        val count: Int,
        val total: Int,
        val transactions: List<APITransaction>
    )
    suspend fun transactions(
        address: String,
        limit: Int = 25,
        offset: Int = 0
    ) = client.get<TransactionResponse>("$ROOT/transactions/$address") {
        parameter("order", "DESC")
        parameter("limit", limit)
        parameter("offset", offset)
    }

    companion object {
        private const val ROOT = "/lookup"
    }
}
