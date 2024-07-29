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
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity

data class CheckAmbiguityC2SPacket(val address: String): KristPayC2SPacket() {
    override val id = CheckAmbiguityC2SPacket.id

    override fun toBytes(buf: PacketByteBuf) {
        buf.writeString(address)
    }

    override fun onServerReceive(
        server: MinecraftServer,
        player: ServerPlayerEntity,
        handler: ServerPlayNetworkHandler,
        responseSender: PacketSender
    ) {
        EVENT.invoker().invoke(this, player, handler, responseSender)
    }

    companion object {
        val id = packetId("check_ambiguity")

        val EVENT = serverPacketEvent<CheckAmbiguityC2SPacket>()

        fun fromBytes(buf: PacketByteBuf) = CheckAmbiguityC2SPacket(
            address = buf.readString()
        )
    }
}
