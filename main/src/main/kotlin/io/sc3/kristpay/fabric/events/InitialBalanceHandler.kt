/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.events

import net.minecraft.server.network.ServerPlayerEntity
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.fabric.shared.packet.BalanceUpdateS2CPacket

object InitialBalanceHandler: KristPayConsumer() {
    fun handleLogin(player: ServerPlayerEntity) {
        val snapshot = API.getDefaultWalletSnapshot(player.uuid) ?: return
        player.networkHandler?.sendPacket(BalanceUpdateS2CPacket(snapshot.balance.amount).build())
    }
}
