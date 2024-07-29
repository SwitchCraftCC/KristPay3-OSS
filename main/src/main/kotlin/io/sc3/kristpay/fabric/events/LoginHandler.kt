/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.events

import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.fabric.events.welfare.LoginBonusWelfare
import mu.KLoggable
import mu.KLogging
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayerEntity

object LoginHandler: KristPayConsumer(), KLoggable by KLogging() {
    fun handle(player: ServerPlayerEntity) {
        API.initializeUser(player.uuid, player.entityName.lowercase(), giveStartingBalance = true)

        InitialBalanceHandler.handleLogin(player)
        player.server.send(object : ServerTask(0, {
            try {
                LoginBonusWelfare.handleLogin(player)
                NotificationHandlers.handleLogin(player)
            } catch (e: Exception) {
                logger.error(e) { "Exception during login handler!" }
            }
        }) {})

        // TODO: Refactor welfare code out of the frontend into a separate mod
    }
}
