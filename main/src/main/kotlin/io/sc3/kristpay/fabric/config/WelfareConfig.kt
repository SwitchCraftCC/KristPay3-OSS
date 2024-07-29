/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Serializable
data class WelfareConfig(
    val startingBalance: Long = 100,
    val bankName: String = "SwitchCraft",

    val loginBonus: CountBonusConfig = CountBonusConfig(),
    val faucetBonus: StreakBonusConfig = StreakBonusConfig(),
    val rollingDailyBonus: RollingDailyBonusConfig = RollingDailyBonusConfig(),
)

@Serializable
data class StreakBonusConfig(
    val enabled: Boolean = false,
    val progression: List<Long> = listOf(2, 4, 6, 8),
    val periodSeconds: Long = 1.days.inWholeSeconds,
    val gracePeriod: Double = 0.5, // In multiples of period, additive
    // (i.e. gracePeriod = 0.5 means allowed to keep streak for 0.5 periods after the due date)

    val activeTimeRequirementRange: Pair<Long, Long> = Pair(5, 30), // In minutes
    val activeTimeRequirementGraceDays: Long = 5,
) {
    val periodDuration: Duration by lazy { periodSeconds.seconds }
}

@Serializable
data class CountBonusConfig(
    val enabled: Boolean = false,
    val reward: Long = 10,
    val minimumCount: Long = 5,
)

@Serializable
data class RollingDailyBonusConfig(
    val enabled: Boolean = true,
    val days   : Long     = 7,
    val minDays: Long     = 3,

    val dailyMultiplier: Long = 20,
    val maxHours       : Long = 14,

    val checkCron: String = "10 0 0 * * ? *", // 10 seconds after midnight
    val doUninitializedPayout: Boolean = false
)
