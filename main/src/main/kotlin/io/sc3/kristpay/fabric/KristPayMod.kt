/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric

import io.sc3.kristpay.core.KristPay
import io.sc3.kristpay.fabric.command.CommandObject
import io.sc3.kristpay.fabric.command.KristPayCommand
import io.sc3.kristpay.fabric.events.LoginHandler
import io.sc3.kristpay.fabric.events.welfare.RollingDailyWelfare
import io.sc3.kristpay.fabric.shared.packet.AmbiguityResponseS2CPacket
import io.sc3.kristpay.fabric.shared.packet.CheckAmbiguityC2SPacket
import io.sc3.kristpay.fabric.shared.packet.registerServerReceiver
import io.sc3.kristpay.util.ClassScanner
import mu.KLogging
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents


class KristPayMod : ModInitializer, KLogging() {

    override fun onInitialize() {
//        LOG.info("YAY: ${conn.prepareStatement("SELECT 1").executeQuery().row}")

        logger.info("KristPay initializing...")

        registerServerReceiver(CheckAmbiguityC2SPacket.id, CheckAmbiguityC2SPacket::fromBytes)

        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting {
            val kristPay = KristPay(FabricFrontend(it))

            CheckAmbiguityC2SPacket.EVENT.register { packet, _, _, responseSender ->
                responseSender.sendPacket(AmbiguityResponseS2CPacket(
                    address = packet.address,
                    exists = kristPay.findWalletByName(packet.address) != null
                ).build())
            }

            ServerLifecycleEvents.SERVER_STOPPING.register(ServerStopping {
                kristPay.cleanup() // Kill active websocket sessions
            })

            RollingDailyWelfare.initJob()
        })

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            LoginHandler.handle(handler.player)
        }

        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, _, _ ->
            val commands = ClassScanner.getObjectsAnnotatedWith(KristPayCommand::class, CommandObject::class)
            commands.forEach {
                it.register(dispatcher)
            }
        })

//        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher, registryAccess, environment ->
//
//        })

//           .INSTANCE.register(false) { dispatcher ->
//            dispatcher.register(
//                CommandManager.literal("fabric_test")
//                    .executes { c: CommandContext<ServerCommandSource> ->
//                        c.source.sendFeedback(StringTextComponent("Command works!"), false)
//                        Command.SINGLE_SUCCESS
//                    }
//            )
//        }
    }
}
