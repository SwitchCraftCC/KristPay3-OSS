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
import io.sc3.kristpay.api.model.integrations.ActiveTimeIntegration
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.core.welfare.WelfareType
import io.sc3.kristpay.model.PromotionTargets
import io.sc3.kristpay.model.Welfare
import io.sc3.kristpay.model.WelfareBenefits
import io.sc3.kristpay.model.atomicIncrement
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KLoggable
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration

object FaucetWelfare: KristPayConsumer(), KLoggable by KLogging() {
    private val faucetCounter = Counter.build()
        .name("kristpay_welfare_faucet")
        .help("Number of times the faucet was claimed")
        .register()

    private val config by CONFIG::welfare

    sealed class ClaimResult {
        class Success(val nextReward: MonetaryAmount): ClaimResult()
        class AlreadyClaimed(val remainingTime: Duration): ClaimResult()
        object RequiredActiveTimeNotMet : ClaimResult()
        object WelfareDisabled : ClaimResult()
    }

    fun handleClaimFaucet(userID: UserID): ClaimResult = transaction(kpdb) {
        val userWelfare = WelfareBenefits.findByUser(userID)
            ?: throw RuntimeException("User $userID has no welfare benefits!!!") // TODO metric?

        if (userWelfare.disabled) return@transaction ClaimResult.WelfareDisabled

        val now = Clock.System.now()
        val prev = userWelfare.lastDispensedFaucet

        val delta = now - prev
        if (delta < config.faucetBonus.periodDuration) {
            return@transaction ClaimResult.AlreadyClaimed(config.faucetBonus.periodDuration - delta)
        }

        if (ActiveTimeIntegration.INSTANCE != null) {
            val activeTime = ActiveTimeIntegration.INSTANCE!!.getActiveTime(userID)
            if (activeTime < userWelfare.requiredActiveTime && userWelfare.loginCount >= config.faucetBonus.activeTimeRequirementGraceDays) {
                return@transaction ClaimResult.RequiredActiveTimeNotMet
            } else {
                userWelfare.requiredActiveTime = activeTime + generateActiveTimeRequirement() *60
            }
        }
        // TODO: delta metric

        val streakDeadline = prev + config.faucetBonus.periodDuration * (1 + config.faucetBonus.gracePeriod)
        if (streakDeadline < now) { // TODO: metric how often streaks are broken
            userWelfare.faucetStreak = 1 // Reset the streak
        } else {
            userWelfare.atomicIncrement(Welfare, Welfare.faucetStreak)
        }

        userWelfare.lastDispensedFaucet = now
        dispenseFaucetBonus(userID, userWelfare)
        return@transaction ClaimResult.Success(nextReward = MonetaryAmount(calculateFaucetBonus(userWelfare.faucetStreak + 1)))
    }

    private fun generateActiveTimeRequirement(): Long {
        val min = config.faucetBonus.activeTimeRequirementRange.first
        val max = config.faucetBonus.activeTimeRequirementRange.second

        return (min..max).random()
    }

    private fun dispenseFaucetBonus(userID: UserID, userWelfare: WelfareBenefits) {
        val amount = WelfareHandlers.applyPromotions(
            PromotionTargets.FAUCET,
            userID,
            calculateFaucetBonus(userWelfare.faucetStreak)
        )

        val wallet = API.getDefaultWallet(userID)!!
        if (amount > 0) {
            API.initializeTransaction(
                initiator = Initiator.Server,
                from = KristPayUnallocated,
                to = KristPayWallet(wallet),
                amount = MonetaryAmount(amount),
                systemMetadata = buildJsonObject {
                    put(WELFARE_TX_CLASS, WelfareType.FAUCET.name)
                },
                sendNotification = false
            )

            faucetCounter.inc()
        }
    }

    // Must be called AFTER the streak is incremented
    private fun calculateFaucetBonus(faucetStreak: Int): Int = when {
        !config.faucetBonus.enabled -> 0
        else -> config.faucetBonus.progression.let { it.getOrNull(faucetStreak - 1) ?: it.last() }.toInt()
    }
}
