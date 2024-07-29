/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model.integrations

import java.util.*

typealias TotalTime = Long
typealias AfkTime = Long
@JvmInline value class UserAfkTimeStatus(val status: Pair<AfkTime, TotalTime>)

interface ActiveTimeIntegration {
    fun getStatus(uuid: UUID): UserAfkTimeStatus
    fun getActiveTime(uuid: UUID): Long = getStatus(uuid).let { it.status.second - it.status.first }

    /**
     * Returns a map of players who have been active in the last N days, along with their play time, in seconds,
     * during that time period. Used for rolling daily welfare.
     *
     * @param day The 'current' day, in the format "YYYY-MM-DD" (could be a date in the past - checks should be done
     *            from this date backwards)
     * @param days The number of days to look back
     * @param minDays The minimum number of unique days a player must have played on to be eligible for the welfare
     */
    fun getActiveUsers(
        day: String,
        days: Int,
        minDays: Int
    ): Map<UUID, Long> = emptyMap()

    companion object {
        var INSTANCE: ActiveTimeIntegration? = null
            set(value) = if (field == null) field = value
            else throw IllegalStateException("ActiveTimeIntegration.INSTANCE is already set!")
    }
}
