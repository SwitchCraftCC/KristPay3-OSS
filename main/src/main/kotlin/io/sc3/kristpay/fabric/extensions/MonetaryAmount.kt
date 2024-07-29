/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.extensions

import net.minecraft.text.MutableText
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.text.formatKristValue

fun MonetaryAmount.toText(long: Boolean = false): MutableText {
//    return of("${formatValue()} ${CONFIG.frontend.currencySymbol}", STYLE.accent)
    return formatKristValue(amount, long, *STYLE.accent.toTypedArray())
//    Text.literal(this.amount.toString()).formatted(Formatting.BOLD).append(
//        Text.literal(MonetaryAmount.CURRENCY_SYMBOL).formatted(Formatting.RESET, Formatting.GREEN))
}
