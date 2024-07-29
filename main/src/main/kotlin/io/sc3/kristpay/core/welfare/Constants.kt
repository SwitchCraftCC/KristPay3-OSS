/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.welfare

const val WELFARE_TX_CLASS = "welfare"
const val REVERSAL_OF = "reversalOf" // Identifies the transactions being reversed
const val IS_REVERSED = "isReversed" // Identifies if the transaction is reversed
enum class WelfareType {
    STARTING_BALANCE,    // Starting balance for a new account
    LOGIN_BONUS,         // Bonus for logging in
    FAUCET,              // Running /faucet
    ROLLING_DAILY_BONUS, // Daily bonus based on last 7 days of playtime

    REVERSAL, // Automatic reversal of a welfare reward
}
