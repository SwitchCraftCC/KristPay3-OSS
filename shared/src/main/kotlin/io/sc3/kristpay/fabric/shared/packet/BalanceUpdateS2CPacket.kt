/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.shared.packet

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.PacketByteBuf

data class BalanceUpdateS2CPacket(val balance: Int): KristPayS2CPacket() {
    override val id = BalanceUpdateS2CPacket.id

    override fun toBytes(buf: PacketByteBuf) {
        buf.writeInt(balance)
    }

    override fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        responseSender: PacketSender
    ) {
        EVENT.invoker().invoke(this)
    }

    companion object {
        val id = packetId("balance_update")

        val EVENT = clientPacketEvent<BalanceUpdateS2CPacket>()

        fun fromBytes(buf: PacketByteBuf) = BalanceUpdateS2CPacket(
            balance = buf.readInt()
        )
    }
}
