/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import io.sc3.kristpay.api.WalletID

interface ServicePaymentPlan {
    fun execute(recipientWallet: WalletID)
}

interface ServicePayment {
    val product: ServiceProduct
    val transaction: TransactionSnapshot

    // Call accept to acknowledge the payment
    // and confirm that any benefits have been applied
    fun accept()

    // This is implicitly called if accept() is not called
    fun reject()
}
