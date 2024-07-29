/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.http

import kotlinx.serialization.Serializable

class AddressesController(private val client: KristHTTP) {
    @Serializable data class AddressResponse(
        val address: APIAddress
    )
    suspend fun get(
        address: String
    ) = client.get<AddressResponse>("$ROOT/$address")

    companion object {
        private const val ROOT = "/addresses"
    }
}
