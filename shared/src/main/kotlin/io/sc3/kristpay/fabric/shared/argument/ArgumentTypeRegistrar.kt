/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.shared.argument

import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer
import net.minecraft.command.suggestion.SuggestionProviders
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier


object ArgumentTypeRegistrar {
    lateinit var PayableArgumentSuggestionProvider: SuggestionProvider<ServerCommandSource>

    fun register() {
        ArgumentTypeRegistry.registerArgumentType(
            Identifier("kristpay", "payable"),
            PayableArgumentType::class.java,
            PayableArgumentType.Serializer()
        )

        ArgumentTypeRegistry.registerArgumentType(
            Identifier("kristpay", "address"),
            PayableArgumentType::class.java,
            PayableArgumentType.Serializer()
        )

        PayableArgumentSuggestionProvider = SuggestionProviders.register(
            Identifier("kristpay", "payable"),
            PayableArgumentType::listSuggestions
        )
    }
}
