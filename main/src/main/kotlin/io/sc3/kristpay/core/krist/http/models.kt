/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class APITransactionType {
    @SerialName("mined") Mined,
    @SerialName("transfer") Transfer,
    @SerialName("name_purchase") NamePurchase,
    @SerialName("name_a_record") NameARecord,
    @SerialName("name_transfer") NameTransfer
}

@Serializable
data class APITransaction(
    val id: Int,
    val from: String,
    val to: String,
    val value: Int,
    val time: String,
    val name: String?,
    val metadata: String?,
    val sent_metaname: String?,
    val sent_name: String?,
    val type: APITransactionType
)

@Serializable
data class APIAddress(
    val address: String,
    val balance: Int,
    val totalin: Int,
    val totalout: Int,
    val firstseen: String, // TODO: ISO-8601 string
)
