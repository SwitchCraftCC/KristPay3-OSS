/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.shared.argument

import com.google.gson.JsonObject
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.serialize.ArgumentSerializer
import net.minecraft.network.PacketByteBuf
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class PayableArgumentType(private val onlyAddress: Boolean): ArgumentType<PayableArgumentType.Payable> {
    sealed class Payable(private val raw: String) {
        fun format(): String = raw
    }

    data class Name(
        val metaname: String?,
        val name: String
    ) : Payable(
        "${metaname?.plus("@") ?: ""}${name}.kst"
    ) { companion object {
        fun from(str: String) = nameRegex.matchEntire(str)?.run { Name(groupValues[1].ifEmpty { null }, groupValues[2]) }
    } }

    data class AddressOrPlayer(val target: String) : Payable(target) { companion object {
        fun from(str: String) = addressRegex.matchEntire(str)?.run { AddressOrPlayer(str) }
    } }

    data class Address(val address: String) : Payable(address) { companion object {
        fun from(str: String) = addressRegex.matchEntire(str)?.run { Address(str) }
    } }

    data class Username(val username: String) : Payable(username) { companion object {
        fun from(str: String) = minecraftUsernameRegex.matchEntire(str)?.run { Username(str) }
    } }

    override fun parse(reader: StringReader): Payable {
        val argBeginning = reader.cursor
        if (!reader.canRead()) {
            reader.skip()
        }

        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip()
        }

        val target = reader.string.substring(argBeginning, reader.cursor)

        return if (onlyAddress) {
            Address.from(target) ?: throw INVALID_ADDRESS_EXCEPTION.createWithContext(reader)
        } else {
            if (target.startsWith("player:")) {
                return Username.from(target.removePrefix("player:"))
                    ?: throw INVALID_TARGET_EXCEPTION.createWithContext(reader)
            }

            if (target.startsWith("address:")) {
                return Address.from(target.removePrefix("address:"))
                    ?: throw INVALID_TARGET_EXCEPTION.createWithContext(reader)
            }

            Name.from(target)
                ?: AddressOrPlayer.from(target)
                ?: Username.from(target)
                ?: throw INVALID_TARGET_EXCEPTION.createWithContext(reader)
        }
    }

    override fun getExamples() = listOf("k123456789", "kabc123def", "abc@def.kst", "abc.kst", "username")

    class Serializer: ArgumentSerializer<PayableArgumentType, Serializer.Properties> {
        override fun writePacket(properties: Properties, packetByteBuf: PacketByteBuf) {
            packetByteBuf.writeBoolean(properties.onlyAddress)
        }

        override fun fromPacket(packetByteBuf: PacketByteBuf): Properties {
            val onlyAddress = packetByteBuf.readBoolean()
            return Properties(onlyAddress)
        }

        override fun writeJson(properties: Properties, jsonObject: JsonObject) {
            jsonObject.addProperty(
                "onlyAddress", properties.onlyAddress
            )
        }

        override fun getArgumentTypeProperties(argumentType: PayableArgumentType): Properties {
            return Properties(argumentType.onlyAddress)
        }

        inner class Properties(val onlyAddress: Boolean) :
            ArgumentSerializer.ArgumentTypeProperties<PayableArgumentType> {
            override fun createType(commandRegistryAccess: CommandRegistryAccess?): PayableArgumentType {
                return when (onlyAddress) {
                    true -> address()
                    false -> payable()
                }
            }

            override fun getSerializer(): ArgumentSerializer<PayableArgumentType, *> {
                return this@Serializer
            }
        }
    }

    companion object {
        fun payable(): PayableArgumentType {
            return PayableArgumentType(onlyAddress = false)
        }

        fun address(): PayableArgumentType {
            return PayableArgumentType(onlyAddress = true)
        }

        fun getPayable(context: CommandContext<*>, name: String?): Payable {
            return context.getArgument(name, Payable::class.java)
        }

        val extraProviders = mutableListOf<SuggestionProvider<CommandSource>>()

        val userCache = ConcurrentHashMap<String, Boolean>()
        private fun isAmbiguous(source: CommandSource, target: String)
            = addressRegex.matchEntire(target) != null && (
                userCache[target] == true || source.playerNames.contains(target)
            )

        fun listSuggestions(
            context: CommandContext<CommandSource>,
            builder: SuggestionsBuilder
        ): CompletableFuture<Suggestions> {
            val remaining = builder.remaining
            if (isAmbiguous(context.source, remaining)) {
                builder.suggest("player:$remaining")
                builder.suggest("address:$remaining")
                return builder.buildFuture()
            }

            extraProviders.forEach { it.getSuggestions(context, builder) }

            if (builder.build().isEmpty) {
                val player = MinecraftClient.getInstance().player
                val names = context.source.playerNames.filter { it != player?.entityName }
                CommandSource.suggestMatching(names, builder)
            }

            return builder.buildFuture()
        }

        val nameRegex = Regex("\\b(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kst\\b", RegexOption.IGNORE_CASE)
        val addressRegex = Regex("\\bk[a-z0-9]{9}\\b")
        val minecraftUsernameRegex = Regex("\\b\\w{3,16}\\b")

        val INVALID_ADDRESS_EXCEPTION = SimpleCommandExceptionType { "Invalid recipient; expected Krist address" }
        val INVALID_TARGET_EXCEPTION = SimpleCommandExceptionType { "Invalid recipient; expected username, Krist address, or Krist name" }
    }
}
