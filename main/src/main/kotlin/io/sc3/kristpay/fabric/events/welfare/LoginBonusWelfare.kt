/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.events.welfare

import io.prometheus.client.Counter
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.UserID
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.KristPayUnallocated
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.krist.CommonMeta
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.core.welfare.WelfareType
import io.sc3.kristpay.fabric.events.welfare.WelfareHandlers.Permission
import io.sc3.kristpay.model.PromotionTargets
import io.sc3.kristpay.model.Welfare
import io.sc3.kristpay.model.WelfareBenefits
import io.sc3.kristpay.model.atomicIncrement
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.lucko.fabric.api.permissions.v0.Permissions
import mu.KLoggable
import mu.KLogging
import net.minecraft.server.network.ServerPlayerEntity
import org.jetbrains.exposed.sql.transactions.transaction

object LoginBonusWelfare: KristPayConsumer(), KLoggable by KLogging() {
    private val loginBonusCounter = Counter.build()
        .name("kristpay_welfare_login_bonus")
        .help("Number of login bonuses claimed")
        .register()

    private val config by CONFIG::welfare

    fun handleLogin(user: ServerPlayerEntity) {
        // Allow disabling login bonus by permissions
        if (Permissions.check(user, Permission.CLAIM_LOGIN_BONUS, 0)) {
            checkLoginBonus(user.uuid)
        }
    }

    private fun checkLoginBonus(userID: UserID): Boolean = transaction(kpdb) {
        val userWelfare = WelfareBenefits.findByUser(userID) ?: return@transaction false.also {
            logger.error("User $userID has no welfare benefits!!!") // TODO metric?
        }

        if (userWelfare.disabled) return@transaction false

        val now = Clock.System.now()

        val prevDate = userWelfare.lastLogin.toLocalDateTime(TimeZone.UTC).date
        val nowDate = now.toLocalDateTime(TimeZone.UTC).date

        val daysSinceLastLogin = prevDate.daysUntil(nowDate)
        // TODO: Days since last login metric
        userWelfare.lastLogin = now
        if (daysSinceLastLogin > 0) {
            userWelfare.atomicIncrement(Welfare, Welfare.loginCount)
            dispenseLoginBonus(userID, userWelfare)
            return@transaction true
        } else {
            return@transaction false
        }
    }

    private fun dispenseLoginBonus(userID: UserID, userWelfare: WelfareBenefits) {
        val amount = WelfareHandlers.applyPromotions(
            PromotionTargets.LOGIN_BONUS,
            userID,
            calculateLoginBonus(userWelfare.loginCount)
        )

        val wallet = API.getDefaultWallet(userID)!!
        if (amount > 0) {
            API.initializeTransaction(
                initiator = Initiator.Server,
                from = KristPayUnallocated,
                to = KristPayWallet(wallet),
                amount = MonetaryAmount(amount),
                metadata = CommonMeta(
                    "message" to "Thank you for playing! As a reward, you have been given $amount KST for logging in!"
                ).toString(),
                systemMetadata = buildJsonObject {
                    put(WELFARE_TX_CLASS, WelfareType.LOGIN_BONUS.name)
                }
            )

            loginBonusCounter.inc()
        }
    }

    // Must be called AFTER the count is incremented
    private fun calculateLoginBonus(loginCount: Int): Int = when {
        !config.loginBonus.enabled -> 0
        loginCount < config.loginBonus.minimumCount -> 0
        else -> config.loginBonus.reward.toInt()
    }
}
