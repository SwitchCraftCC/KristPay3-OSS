/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.util.*

@Serializable
sealed class PromotionEffect {
    abstract fun apply(base: Int): Int
    abstract val order: Int // lower order is applied first
}

@Serializable
data class MultiplierEffect(val multiplier: Double) : PromotionEffect() {
    override fun apply(base: Int): Int = (base * multiplier).toInt()
    override val order: Int get() = 0
}

@Serializable
data class BonusEffect(val bonus: Int) : PromotionEffect() {
    override fun apply(base: Int): Int = base + bonus
    override val order: Int get() = 1
}

enum class PromotionTargets {
    LOGIN_BONUS,
    FAUCET,
    ROLLING_DAILY_BONUS
}

class Promotion(id: EntityID<UUID>) : BaseUUIDEntity(id, Promotions) {
    var name by Promotions.name
    var effect by Promotions.effect.useJSON<PromotionEffect>(Promotion)
    var targetGroup by UserGroup optionalReferencedOn Promotions.targetGroup

    var periodStart by Promotions.periodStart
    var periodEnd by Promotions.periodEnd

    companion object : UUIDEntityClass<Promotion>(Promotions)
}

@KristPayModelTable
object Promotions : BaseUUIDTable("promotions") {
    val name = varchar("name", 255)
    val effect = text("effect")
    val targetBonus = enumeration<PromotionTargets>("target_bonus")
    val targetGroup = reference("target_group", UserGroups).nullable()
    val periodStart = datetime("period_start").nullable()
    val periodEnd = datetime("period_end").nullable()
}
