/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.http

import kotlinx.serialization.Serializable

class TransactionsController(private val client: KristHTTP) {
    @Serializable
    data class MakeTransactionRequest(
        val privatekey: String,
        val to: String,
        val amount: Int,
        val metadata: String? = null,
        val requestId: String? = null,
    )
    @Serializable
    data class MakeTransactionResponse(val transaction: APITransaction)
    suspend fun makeTransaction(request: MakeTransactionRequest)
    = client.post<MakeTransactionRequest, MakeTransactionResponse>(ROOT, request)

    suspend fun getTransaction(id: Int) = client.get<APITransaction>("$ROOT/$id")

    companion object {
        private const val ROOT = "/transactions"
    }
}
