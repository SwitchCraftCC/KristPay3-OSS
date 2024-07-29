/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.payment.strategy

import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.PaymentResult
import io.sc3.kristpay.core.payment.PaymentStrategy
import io.sc3.kristpay.model.Transaction

object UnallocatedPaymentStrategy: PaymentStrategy {
    override suspend fun debit(amount: MonetaryAmount, pendingTransaction: Transaction, allowDebt: Boolean): PaymentResult
        = PaymentResult.Success // TODO: Check if there is enough in master balance

    override suspend fun checkPendingCredit(pendingTransaction: Transaction): PaymentResult {
        throw UnsupportedOperationException("UnallocatedPaymentStrategy does not support pending credits")
    }

    override suspend fun credit(amount: MonetaryAmount, pendingTransaction: Transaction): PaymentResult
        = PaymentResult.Success // Nothing to do...

    override suspend fun checkPendingDebit(pendingTransaction: Transaction): PaymentResult {
        throw UnsupportedOperationException("UnallocatedPaymentStrategy does not support pending debits")
    }
}
