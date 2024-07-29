/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import java.util.UUID

sealed class Initiator {
    data class User(val id: UUID): Initiator()
    data class KristAddress(val address: String): Initiator()
    data class ServerPlugin(val pluginID: String): Initiator()
    object Server: Initiator()

    fun serialize(): String {
        return when (this) {
            is User -> "$UserKey:$id"
            is KristAddress -> "$AddressKey:$address"
            is ServerPlugin -> "$PluginKey:$pluginID"
            is Server -> ServerKey
        }
    }

    companion object {
        private const val UserKey = "User"
        private const val AddressKey = "Krist"
        private const val PluginKey = "Plugin"
        private const val ServerKey = "Server"

        fun deserialize(record: String): Initiator {
            val prefix = record.substringBefore(":")
            val value = record.substringAfter(":")
            return when (prefix) {
                UserKey -> User(UUID.fromString(value))
                AddressKey -> KristAddress(value)
                PluginKey -> ServerPlugin(value)
                ServerKey -> Server
                else -> throw IllegalArgumentException("Invalid initiator record: $record")
            }
        }
    }
}
