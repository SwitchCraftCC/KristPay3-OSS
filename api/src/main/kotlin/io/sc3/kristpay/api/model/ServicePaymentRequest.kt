/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import kotlinx.serialization.Serializable
import io.sc3.kristpay.api.model.serializers.UUIDSerializer
import java.util.*

typealias PluginID = String
typealias GroupID = String

// The combination of groupID and uniqueID should be able to
// reconstruct the product/service intent purchased by the payment.
// Typically, the uniqueID is the player uuid.
// Both of these values are plugin managed and are opaque to KristPay
@Serializable
data class ServiceProduct(
    val groupID: GroupID,
    val uniqueID: String,
    val friendlyName: String
)

@Serializable
data class ServicePaymentRequest(
    val requesterName: String, // Displayed to the user when they are asked to confirm the payment
    val pluginID: String, // The plugin ID of the plugin requesting the payment
    val product: ServiceProduct,
    val amount: MonetaryAmount,

    @Serializable(with = UUIDSerializer::class)
    val fromUser: UUID
)
