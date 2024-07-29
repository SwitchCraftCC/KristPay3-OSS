/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.fabric.text.buildText
import io.sc3.text.copyable
import io.sc3.text.of
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource

@KristPayCommand
object DepositCommand: CommandObject {
    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        dispatcher.register(
            literal("deposit")
                .executes {
                    val player = it.source.playerOrThrow

                    it.source.sendFeedback({
                        of("Your deposit address: ", STYLE.primary) + buildText {
                            +STYLE.accent
                            +player.entityName.lowercase() - "@" - CONFIG.krist.advertisedName - ".kst"
                        }.copyable()
                    }, false)

                    Command.SINGLE_SUCCESS
                }
        )
    }
}
