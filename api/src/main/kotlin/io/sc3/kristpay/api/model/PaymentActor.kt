/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import java.util.UUID

sealed class PaymentActor {
    fun serialize(): String {
        return when (this) {
            is KristPayWallet -> "$WalletKey:$walletId"
            is KristAddress -> "$AddressKey:$address"
            is KristPayUnallocated -> UnallocatedKey
        }
    }

    companion object {
        private const val WalletKey = "Managed"
        private const val AddressKey = "Krist"
        private const val UnallocatedKey = "Unallocated"

        fun deserialize(record: String): PaymentActor {
            val prefix = record.substringBefore(":")
            val value = record.substringAfter(":")
            return when (prefix) {
                WalletKey -> KristPayWallet(value)
                AddressKey -> KristAddress(value)
                UnallocatedKey -> KristPayUnallocated
                else -> throw IllegalArgumentException("Unknown actor type: $prefix")
            }
        }
    }
}

data class KristPayWallet(val walletId: UUID) : PaymentActor() {
    constructor(walletId: String): this(UUID.fromString(walletId))
}

data class KristAddress(val address: String) : PaymentActor() {
    val isName by lazy { address.endsWith(".kst") }
    val name by lazy { if (isName) address.substringBeforeLast(".").substringAfter("@") else null }
    val metaname by lazy { if (isName) address.substringBeforeLast("@", "").ifEmpty { null } else null }
}

object KristPayUnallocated : PaymentActor()
