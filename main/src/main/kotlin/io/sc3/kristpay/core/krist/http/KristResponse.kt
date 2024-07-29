/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.http

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class KristOkResponse(
    val ok: Boolean
)

enum class KristError(val code: String) {
    NameNotFound("name_not_found"),
    ServerError("server_error"),
    RateLimited("rate_limited"),
    InsufficientFunds("insufficient_funds"),
    TransactionConflict("transaction_conflict"),
    InvalidParameter("invalid_parameter"),
    MissingParameter("missing_parameter"),
    TransactionsDisabled("transactions_disabled"),
}

@Serializable
data class KristErrorResponse(
    val error: String,
    val message: String? = null,
    val parameter: String? = null
) {
    val code get() = KristError.values().firstOrNull { it.code == error }
}

class KristHTTPException(
    val error: KristErrorResponse
) : Exception(error.message)

val FunnyJson = Json {
    ignoreUnknownKeys = true
}

inline fun <reified T> okOrThrow(source: String): T {
    val `is` = FunnyJson.decodeFromString<KristOkResponse>(source)
    if (`is`.ok) {
        return FunnyJson.decodeFromString(source)
    } else {
        throw KristHTTPException(FunnyJson.decodeFromString(source))
    }
}
