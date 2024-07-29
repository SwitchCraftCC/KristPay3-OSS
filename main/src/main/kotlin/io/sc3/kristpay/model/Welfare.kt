/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import io.sc3.kristpay.api.UserID
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

class WelfareBenefits(id: EntityID<UUID>) : BaseUUIDEntity(id, Welfare) {
    var user by User referencedOn Welfare.user

    var disabled by Welfare.disabled

    var lastDispensedFaucet by Welfare.lastDispensedFaucet
    var faucetStreak by Welfare.faucetStreak
    var requiredActiveTime by Welfare.requiredActiveTime

    var lastLogin by Welfare.lastLogin
    var loginCount by Welfare.loginCount

    var rollingDailyFractionalKrist by Welfare.rollingDailyFractionalKrist

    companion object : BaseUUIDEntityClass<WelfareBenefits>(Welfare) {
        fun findByUser(userID: UserID) = find { Welfare.user eq userID }.firstOrNull()
    }
}

@KristPayModelTable
object Welfare : BaseUUIDTable("welfare") {
    val user: Column<EntityID<UUID>> = reference("user", Users).uniqueIndex()

    val disabled = bool("disabled").default(false)

    val lastDispensedFaucet = timestamp("last_dispensed_faucet").default(Instant.NOT_SO_DISTANT_PAST)
    val faucetStreak = integer("faucet_streak").default(0)
    val requiredActiveTime = long("required_active_time").default(0)
    
    val lastLogin = timestamp("last_login").default(Instant.NOT_SO_DISTANT_PAST)
    val loginCount = integer("login_count").default(0)

    var rollingDailyFractionalKrist = decimal("rolling_daily_fractional_krist", 20, 8).default(0.toBigDecimal())
}
