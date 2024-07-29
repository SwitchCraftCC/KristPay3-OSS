/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.ws

import kotlinx.serialization.Serializable

@Serializable data class SubscribeRequest(val event: String) : KWSRequest("subscribe")
@Serializable data class UnsubscribeRequest(val event: String) : KWSRequest("unsubscribe")

enum class SubscriptionLevels {
    Blocks,
    OwnBlocks,
    Transactions,
    OwnTransactions,
    Names,
    OwnNames,
    Motd;

    fun toWire() = name.replaceFirstChar { it.lowercase() }

    companion object {
        suspend fun subscribe(vararg levels: SubscriptionLevels)
            = levels.forEach { SubscribeRequest(it.toWire()).send() }

        suspend fun unsubscribe(vararg levels: SubscriptionLevels)
            = levels.forEach { UnsubscribeRequest(it.toWire()).send() }
    }
}
