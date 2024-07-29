/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.events

import net.minecraft.server.network.ServerPlayerEntity
import org.jetbrains.exposed.sql.transactions.transaction
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.model.User
import io.sc3.text.formatKristValue

object NotificationHandlers: KristPayConsumer() {
    fun handleLogin(user: ServerPlayerEntity) {
        val notifications = API.getNotifications(user.uuid)
        if (notifications.isEmpty()) return

        val sum = transaction(kpdb) { MonetaryAmount(notifications.sumOf { notification ->
            val tx = notification.referenceTransaction

            // Other participant will always be tx.from except when the user is not the initiator (i.e. a forced tx)
            val wasCredit = tx.to == KristPayWallet(User.findById(notification.user)!!.defaultWalletId.value)
            if (wasCredit) tx.amount.amount else -tx.amount.amount
        }) }

        if (sum.amount > 0) {
            user.sendMessage(formatKristValue(sum.amount, formatting = STYLE.accent) + of(" was deposited into your account since your last login.", STYLE.primary))
        } else if (sum.amount < 0) {
            user.sendMessage(formatKristValue(-sum.amount, formatting = STYLE.accent) + of(" was withdrawn from your account since your last login.", STYLE.warning))
        }

        API.clearNotifications(user.uuid)
    }
}
