/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import org.jetbrains.exposed.dao.id.EntityID

class LastRollingDailyWelfareRow(id: EntityID<Int>) : BaseIntEntity(id, LastRollingDailyWelfareTable) {
    var lastRan by LastRollingDailyWelfareTable.lastRan

    companion object : BaseIntEntityClass<LastRollingDailyWelfareRow>(LastRollingDailyWelfareTable) {
        fun get() = findById(1)
        fun firstSet(value: String) = new(1) {
            lastRan = value
        }
    }
}

// Lol only 1 row
@KristPayModelTable
object LastRollingDailyWelfareTable : BaseIntIdTable("last_rolling_daily_welfare") {
    val lastRan = varchar("last_ran", 32)
}
