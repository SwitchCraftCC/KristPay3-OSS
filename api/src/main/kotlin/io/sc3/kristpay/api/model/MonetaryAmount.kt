/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import kotlinx.serialization.Serializable

@Serializable
data class MonetaryAmount(
    val amount: Int
): Comparable<MonetaryAmount> {
    init {
//        require(amount >= 0) { "Amount must be positive (was $amount)" }
    }

    fun formatValue() = "$amount"

    operator fun plus(other: MonetaryAmount) = MonetaryAmount(amount + other.amount)
    operator fun minus(other: MonetaryAmount) = MonetaryAmount(amount - other.amount)

    override fun compareTo(other: MonetaryAmount): Int = amount.compareTo(other.amount)
    operator fun times(multiplier: Double): MonetaryAmount = MonetaryAmount((amount * multiplier).toInt())

    companion object {
        val ZERO = MonetaryAmount(0)
    }

}
