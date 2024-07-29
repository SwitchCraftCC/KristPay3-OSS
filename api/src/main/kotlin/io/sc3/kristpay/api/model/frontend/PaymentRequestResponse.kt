/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model.frontend

import io.sc3.kristpay.api.WalletID

data class PaymentRequestResponse(
    val confirmed: Boolean,
    val chosenWallet: WalletID?
)
