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

data class AmbiguityResponseS2CPacket(val address: String, val exists: Boolean): KristPayS2CPacket() {
    override val id = AmbiguityResponseS2CPacket.id

    override fun toBytes(buf: PacketByteBuf) {
        buf.writeString(address)
        buf.writeBoolean(exists)
    }

    override fun onClientReceive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        responseSender: PacketSender
    ) {
        EVENT.invoker().invoke(this)
    }

    companion object {
        val id = packetId("ambiguity_response")

        val EVENT = clientPacketEvent<AmbiguityResponseS2CPacket>()

        fun fromBytes(buf: PacketByteBuf) = AmbiguityResponseS2CPacket(
            address = buf.readString(),
            exists = buf.readBoolean()
        )
    }
}
