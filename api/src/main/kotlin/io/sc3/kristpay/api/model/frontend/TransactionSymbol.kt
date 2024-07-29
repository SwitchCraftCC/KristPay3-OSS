/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model.frontend

interface TransactionSymbol {
    /** The single-character symbol for this transaction type, shown in the transaction list. */
    val symbol: String

    /** The full name of this transaction type, shown when hovering, and in the transaction details. */
    val description: String

    /** The name of the color/formatting to use for this transaction type. The name should be a valid Minecraft
     * Formatting name. The attention color is used in the transaction list. */
    val attentionColor: String

    /** The name of the color/formatting to use for this transaction type. The name should be a valid Minecraft
     * Formatting name. The cooperative color is used when hovering over the type, and in the transaction details. */
    val cooperativeColor: String get() = attentionColor
}
