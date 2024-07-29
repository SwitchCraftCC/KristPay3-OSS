/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import mu.KotlinLogging
import kotlin.time.Duration
import kotlin.time.toJavaDuration

fun Duration.toSimpleString(): String = this.toComponents { days, hours, minutes, seconds, _ ->
    val sb = StringBuilder()
    if (days > 0) {
        sb.append("${days}d ")
    }
    if (hours > 0) {
        sb.append("${hours}h ")
    }
    if (minutes > 0) {
        sb.append("${minutes}m ")
    }
    if (seconds > 0) {
        sb.append("${seconds}s")
    }

    sb.toString()
}

val Duration.ago get() = Clock.System.now() - this


inline fun CoroutineScope.startCoroutineTimer(
    delayDuration: Duration = Duration.ZERO,
    repeatDuration: Duration = Duration.ZERO,
    crossinline action: suspend () -> Unit
) = this.launch {
    val logger = KotlinLogging.logger("CoroutineTimer")

    delay(delayDuration.toJavaDuration())
    if (repeatDuration.isPositive()) {
        while (true) {
            try {
                action()
            } catch (e: Exception) {
                logger.error("Error in coroutine timer", e)
            }
            delay(repeatDuration.toJavaDuration())
        }
    } else {
        action()
    }
}

