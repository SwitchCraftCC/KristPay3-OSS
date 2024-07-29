/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import kotlinx.datetime.Instant
import io.sc3.kristpay.api.UserGroupID
import io.sc3.kristpay.api.WalletID

data class WalletSnapshot(
    val id: WalletID,
    val owner: UserGroupID?,
    val name: String,
    val balance: MonetaryAmount,
    val asOf: Instant
)
