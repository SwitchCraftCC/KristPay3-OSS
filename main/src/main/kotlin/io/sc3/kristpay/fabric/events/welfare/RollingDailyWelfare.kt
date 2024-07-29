/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.events.welfare

import io.prometheus.client.Counter
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.KristPayUnallocated
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.integrations.ActiveTimeIntegration
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.krist.CommonMeta
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.core.welfare.WelfareType
import io.sc3.kristpay.fabric.events.welfare.WelfareHandlers.Permission
import io.sc3.kristpay.model.LastRollingDailyWelfareRow
import io.sc3.kristpay.model.PromotionTargets
import io.sc3.kristpay.model.WelfareBenefits
import io.sc3.text.ScText.log
import it.justwrote.kjob.InMem
import it.justwrote.kjob.KronJob
import it.justwrote.kjob.kjob
import it.justwrote.kjob.kron.Kron
import it.justwrote.kjob.kron.KronModule
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.lucko.fabric.api.permissions.v0.Permissions
import mu.KLoggable
import mu.KLogging
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*
import java.util.concurrent.Executors.newSingleThreadScheduledExecutor

object RollingDailyWelfare: KristPayConsumer(), KLoggable by KLogging() {
    private val config by CONFIG::welfare

    private val maxSeconds      by lazy { config.rollingDailyBonus.maxHours * 3600L }
    private val dailyMultiplier by lazy { config.rollingDailyBonus.dailyMultiplier }
    private val secondsPerHour  = BigDecimal.valueOf(3600)
    private val seven           = BigDecimal.valueOf(7)

    private val rollingDailyCounter = Counter.build()
        .name("kristpay_welfare_rolling_daily_count")
        .help("Number of rolling daily login bonuses claimed")
        .register()

    private val rollingDailyTotalCounter = Counter.build()
        .name("kristpay_welfare_rolling_daily_total")
        .help("Amount of Krist claimed from rolling daily login bonuses")
        .register()

    private val dayFormat = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(ISO_OFFSET_DATE)
        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .toFormatter()

    private val checkExecutor = newSingleThreadScheduledExecutor(BasicThreadFactory.Builder()
        .namingPattern("KristPay-RollingDailyWelfareCheck-%d")
        .daemon(true)
        .build())
    private val checkJob by lazy { object : KronJob(
        "kristpay:rolling-daily-welfare",
        config.rollingDailyBonus.checkCron
    ) {} }

    fun initJob() {
        if (!config.rollingDailyBonus.enabled) {
            log.info("Rolling daily welfare is disabled, not starting job")
            return
        }

        val kjob = kjob(InMem) {
            extension(KronModule)
        }.start()

        kjob(Kron).kron(checkJob) {
            maxRetries = 3
            execute {
                checkExecutor.execute {
                    try {
                        log.info("Running scheduled rolling daily welfare check")
                        performChecks()
                    } catch (e: Exception) {
                        log.error("Failed to run rolling daily welfare check", e)
                    }
                }
            }
        }

        // Run the job when the server has finished starting, too
        ServerLifecycleEvents.SERVER_STARTED.register {
            checkExecutor.execute {
                try {
                    log.info("Running startup rolling daily welfare check")
                    performChecks()
                } catch (e: Exception) {
                    log.error("Failed to run startup rolling daily welfare check", e)
                }
            }
        }

        // Stop the job when the server is stopping, so it doesn't keep the server open
        ServerLifecycleEvents.SERVER_STOPPING.register {
            try {
                log.info("Stopping RollingDailyWelfare kjob")
                kjob.shutdown()
                checkExecutor.shutdown()
            } catch (e: Exception) {
                log.error("Failed to stop RollingDailyWelfare kjob", e)
            }
        }
    }

    private fun performChecks() {
        // Check LastRollingDailyWelfare to get the last day rolling daily welfare was checked. Run the check for each
        // day since then. If the last check was today, do nothing. If it was never checked, check just for today.
        val (lastRanRow, lastRan) = transaction(kpdb) {
            val lastRan = LastRollingDailyWelfareRow.get()
            lastRan to lastRan?.lastRan
        }

        val today = ZonedDateTime.now()
        val todayStr = today.format(dayFormat)

        if (lastRan != null) {
            val then = ZonedDateTime.parse(lastRan, dayFormat)
            log.info("Last ran rolling daily welfare on $then, checking for $todayStr")

            // Get each day since then, as day strings
            val days = then.toLocalDate().datesUntil(today.toLocalDate())
                .map { ZonedDateTime.of(it, today.toLocalTime(), today.zone) }
                .map { it.format(dayFormat) }
                .toList()

            days.forEach { checkRollingDailyWelfare(it) }
        } else if (config.rollingDailyBonus.doUninitializedPayout) {
            log.info("No last ran row found, checking rolling daily welfare for $todayStr")
            checkRollingDailyWelfare(todayStr)
        } else {
            log.info("No last ran row found, doUninitializedPayout is false, not checking rolling daily welfare now")
        }

        // Update the last ran row
        transaction(kpdb) {
            if (lastRanRow == null) {
                LastRollingDailyWelfareRow.firstSet(todayStr)
            } else {
                lastRanRow.lastRan = todayStr
            }
        }
    }

    private fun checkRollingDailyWelfare(day: String) {
        logger.info("Checking rolling daily welfare for $day")

        val activeTime = ActiveTimeIntegration.INSTANCE ?: run {
            logger.error { "No active time integration found, cannot calculate rolling daily welfare" }
            return
        }

        // Query sc-main (or whatever the active time integration is) for the active players in the last N days
        val users = try {
            val cfg = config.rollingDailyBonus
            activeTime.getActiveUsers(day, cfg.days.toInt(), cfg.minDays.toInt())
        } catch (e: Exception) {
            // TODO: Staff Discord alert
            logger.error(e) { "Error getting active users for rolling daily welfare" }
            return
        }

        users.forEach { (uuid, seconds) ->
            try {
                handleRollingDailyWelfareForUser(uuid, seconds)
            } catch (e: Exception) {
                // TODO: Staff Discord alert
                logger.error(e) { "Error calculating rolling daily welfare for user $uuid" }
            }
        }
    }

    private fun handleRollingDailyWelfareForUser(uuid: UUID, seconds: Long) = transaction(kpdb) {
        logger.info("Calculating rolling daily welfare for user $uuid")

        // Perform the permission check - this will block in our scope, as the user may be offline and the permissions
        // may not be cached.
        if (!Permissions.check(uuid, Permission.CLAIM_ROLLING_DAILY).get()) {
            logger.info { "User $uuid does not have permission to claim rolling daily welfare" }
            return@transaction
        }

        val userWelfare = WelfareBenefits.findByUser(uuid) ?: run {
            logger.error { "User $uuid has no welfare benefits, can't apply rolling daily welfare" }
            return@transaction
        }

        if (userWelfare.disabled) {
            logger.info { "User $uuid has welfare disabled, not applying rolling daily welfare" }
            return@transaction
        }

        // Amount of Krist based on the time played in the last N (default 7) days
        val baseAmount = calculateDailyBonus(seconds)

        // Add rollingDailyFractionalKrist to the base amount - this keeps track of the decimal portion of the amount.
        // The user will be paid out the floor of this amount, and the remainder will be kept for the next day.
        val totalAmount = userWelfare.rollingDailyFractionalKrist + baseAmount
        val floorAmount = totalAmount.setScale(0, RoundingMode.FLOOR)
        val remainder = totalAmount - floorAmount

        userWelfare.rollingDailyFractionalKrist = remainder

        // TODO: Promotions are applied to the post-flooring amount, which is a bit weird
        val amountPaidToday = WelfareHandlers.applyPromotions(
            PromotionTargets.ROLLING_DAILY_BONUS,
            uuid,
            floorAmount.toInt()
        )

        if (amountPaidToday > 0) {
            // Pay out the amount
            val wallet = API.getDefaultWallet(uuid) ?: run {
                logger.error { "User $uuid has no wallet, can't pay out rolling daily welfare" }
                return@transaction
            }

            API.initializeTransaction(
                initiator = Initiator.Server,
                from = KristPayUnallocated,
                to = KristPayWallet(wallet),
                amount = MonetaryAmount(amountPaidToday),
                metadata = CommonMeta(
                    "message" to "Thank you for playing! As a reward, you have been given $amountPaidToday KST!"
                ).toString(),
                systemMetadata = buildJsonObject {
                    put(WELFARE_TX_CLASS, WelfareType.ROLLING_DAILY_BONUS.name)
                }
            )

            // Increment the Prometheus metrics
            rollingDailyCounter.inc()
            rollingDailyTotalCounter.inc(amountPaidToday.toDouble())
        }
    }

    private fun calculateDailyBonus(seconds: Long) = BigDecimal
        .valueOf(seconds.coerceAtMost(maxSeconds) * dailyMultiplier)
        .setScale(8)
        .divide(secondsPerHour, RoundingMode.HALF_DOWN)
        .divide(seven, RoundingMode.HALF_DOWN)
}
