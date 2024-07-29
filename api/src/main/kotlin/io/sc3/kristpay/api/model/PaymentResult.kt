/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import java.util.concurrent.CompletableFuture

sealed class PaymentResult {
    object Success : PaymentResult()
    data class Pending(val future: CompletableFuture<PaymentResult>) : PaymentResult()

    abstract class Failure: PaymentResult() {
        abstract fun serialize(): String

        companion object {
            fun deserialize(string: String): Failure {
                return when {
                    string == "InsufficientFunds" -> InsufficientFunds
                    string == "Rejected" -> Rejected
                    string == "InvalidName" -> InvalidName
                    string == "AttemptTimedOut" -> AttemptTimedOut
                    string.startsWith("Error:") -> Error(string.substring(6))
                    string.startsWith("PermanentError:") -> PermanentError(string.substring(15))
                    else -> {
                        throw IllegalArgumentException("Unknown failure type: $string")
                    }
                }
            }
        }
    }

    abstract class PermanentFailure: Failure()

    object InsufficientFunds : PermanentFailure() {
        override fun serialize() = "InsufficientFunds"
    }
    object InvalidName : PermanentFailure() {
        override fun serialize() = "InvalidName"
    }
    object Rejected : PermanentFailure() {
        override fun serialize() = "Rejected"
    }
    object AttemptTimedOut : Failure() {
        override fun serialize() = "AttemptTimedOut"
    }
    data class Error(val error: String) : Failure() {
        override fun serialize() = "Error:${error}"
    }
    data class PermanentError(val error: String) : PermanentFailure() {
        override fun serialize() = "PermanentError:${error}"
    }
}
