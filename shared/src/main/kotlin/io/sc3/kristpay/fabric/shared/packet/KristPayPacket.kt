/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.shared.packet

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.createC2SPacket
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.createS2CPacket
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.Packet
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

internal fun packetId(id: String) = Identifier("kristpay", id)

inline fun <reified T> event(noinline invokerFactory: (Array<T>) -> T): Event<T>
        = EventFactory.createArrayBacked(T::class.java, invokerFactory)

inline fun <reified T> clientPacketEvent() = event<(packet: T) -> Unit> { cb ->
    { packet -> cb.forEach { it(packet) } }
}

inline fun <reified T> serverPacketEvent() = event<(packet: T, player: ServerPlayerEntity,
                                                    handler: ServerPlayNetworkHandler,
                                                    responseSender: PacketSender
) -> Unit> { cb ->
    { packet, player, handler, responseSender -> cb.forEach { it(packet, player, handler, responseSender) } }
}

fun <T: KristPayPacket> registerClientReceiver(id: Identifier, factory: (buf: PacketByteBuf) -> T) {
    ClientPlayNetworking.registerGlobalReceiver(id) { client, handler, buf, responseSender ->
        val packet = factory(buf)
        packet.onClientReceive(client, handler, responseSender)
    }
}

fun <T: KristPayPacket> registerServerReceiver(id: Identifier, factory: (buf: PacketByteBuf) -> T) {
    ServerPlayNetworking.registerGlobalReceiver(id) { server, player, handler, buf, responseSender ->
        val packet = factory(buf)
        packet.onServerReceive(server, player, handler, responseSender)
    }
}

sealed class KristPayPacket {
    abstract val id: Identifier

    abstract fun toBytes(buf: PacketByteBuf)
    open fun toBytes(): PacketByteBuf {
        val buf = PacketByteBufs.create()
        toBytes(buf)
        return buf
    }

    abstract fun build(): Packet<*>

    @Environment(EnvType.CLIENT)
    open fun onClientReceive(client: MinecraftClient, handler: ClientPlayNetworkHandler, responseSender: PacketSender) {}

    @Environment(EnvType.SERVER)
    open fun onServerReceive(server: MinecraftServer, player: ServerPlayerEntity,
                             handler: ServerPlayNetworkHandler, responseSender: PacketSender) {}
}

abstract class KristPayS2CPacket : KristPayPacket() {
    override fun build(): Packet<*> = createS2CPacket(id, toBytes())
}

abstract class KristPayC2SPacket : KristPayPacket() {
    override fun build(): Packet<*> = createC2SPacket(id, toBytes())
}
