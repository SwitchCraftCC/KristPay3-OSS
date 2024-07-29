/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import java.util.UUID

enum class TransactionState {
    INIT,
    DEBIT_PENDING,
    DEBIT_SUCCESS,
    CREDIT_PENDING,
    CREDIT_SUCCESS, // because it is a terminal state of COMPLETE
    CREDIT_FAILED,
    RETRY_CREDIT,
//    CREDIT_FAILED_CONFIRMED,
    REFUND_DEBIT_PENDING,
    REFUND_DEBIT_SUCCESS, // because it is a terminal state of FAILED
    COMPLETE,
    FAILED
}

data class TransactionSnapshot(
    val id: UUID,

    val state: TransactionState,

    val from: PaymentActor,
    val to: PaymentActor,
    val amount: MonetaryAmount,

    val initiator: Initiator,
    val metadata: String?,
    val systemMetadata: JsonObject?,
    val externalReference: Int?,
    val notify: Boolean,

    val timestamp: Instant
)
