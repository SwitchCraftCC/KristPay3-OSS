/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.events

import kotlinx.coroutines.*
import net.minecraft.server.network.ServerPlayerEntity
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.fabric.shared.packet.BalanceUpdateS2CPacket
import io.sc3.kristpay.util.supervised
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object BalancerSender: KristPayConsumer() {
    private val pendingTasks = ConcurrentHashMap<UUID, Job>()
    private val scope = CoroutineScope(Dispatchers.IO.supervised())

    fun sendBalance(player: ServerPlayerEntity) {
        pendingTasks.compute(player.uuid) { _, job ->
            job?.cancel()
            scope.launch {
                delay(500)

                val snapshot = API.getDefaultWalletSnapshot(player.uuid) ?: return@launch
                if (isActive) { // transactions don't suspend
                    player.networkHandler?.sendPacket(BalanceUpdateS2CPacket(snapshot.balance.amount).build())
                }

                if (isActive) {
                    pendingTasks.remove(player.uuid)
                }
            }
        }
    }
}
