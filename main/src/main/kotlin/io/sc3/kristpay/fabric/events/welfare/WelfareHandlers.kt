/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.events.welfare

import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.UserID
import io.sc3.kristpay.api.model.NotificationSnapshot
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.core.welfare.WelfareType
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.getString
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.kristpay.fabric.extensions.plus
import io.sc3.kristpay.fabric.extensions.toText
import io.sc3.kristpay.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mu.KLoggable
import mu.KLogging-
import net.minecraft.server.network.ServerPlayerEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

// TODO: Move welfare out of the frontend into a separate mod/module
object WelfareHandlers: KristPayConsumer(), KLoggable by KLogging() {
    object Permission {
        private const val ROOT        = "kristpay.welfare"
        private const val CLAIM_ROOT  = "$ROOT.claim"
        const val CLAIM_LOGIN_BONUS   = "$CLAIM_ROOT.login"
        const val CLAIM_ROLLING_DAILY = "$CLAIM_ROOT.rolling"
        const val CLAIM_FAUCET        = "$CLAIM_ROOT.faucet"
    }

    fun getWelfareEnabled(userID: UserID): Boolean = transaction(kpdb) {
        WelfareBenefits.findByUser(userID)!!.disabled.not()
    }

    fun setWelfareToggle(userID: UserID, toggle: Boolean): Boolean = transaction(kpdb) {
        val welfare = WelfareBenefits.findByUser(userID)!!
        welfare.disabled = !toggle

        return@transaction welfare.flush()
    }

    fun applyPromotions(promotionTarget: PromotionTargets, user: UserID, base: Int): Int {
        if (base == 0) return 0

        val userGroups = GroupMemberships
            .select { GroupMemberships.member eq user }
            .map { it[GroupMemberships.group] }

        val promotions = getApplicablePromotions(promotionTarget, userGroups)
        return promotions
            .sortedBy { it.effect.order }
            .fold(base) { acc, promotion -> promotion.effect.apply(acc) }
    }

    private fun getApplicablePromotions(
        promotionTarget: PromotionTargets,
        userGroups: List<EntityID<UUID>>
    ): List<Promotion> = Promotion.find { Clock.System.now().toLocalDateTime(TimeZone.UTC).let { now ->
         (Promotions.targetBonus eq promotionTarget)                                 and
        ((Promotions.periodStart eq null) or (Promotions.periodStart lessEq    now)) and
        ((Promotions.periodEnd   eq null) or (Promotions.periodEnd   greaterEq now)) and
        ((Promotions.targetGroup eq null) or (Promotions.targetGroup.inList(userGroups)))
    }}.toList()

    fun handleNotificationPresentation(notification: NotificationSnapshot, player: ServerPlayerEntity): Boolean {
        val tx = notification.referenceTransaction

        when (tx.systemMetadata!!.getString(WELFARE_TX_CLASS)) {
            WelfareType.LOGIN_BONUS.name -> player.sendMessage(tx.amount.toText() +
                of(" was deposited into your account as a daily login bonus!", STYLE.primary))
            WelfareType.ROLLING_DAILY_BONUS.name -> player.sendMessage(tx.amount.toText() +
                of(" was deposited into your account for playing regularly!", STYLE.primary))
            else -> return false
        }

        return true
    }
}
