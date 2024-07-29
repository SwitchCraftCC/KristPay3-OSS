/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.payment

import io.sc3.kristpay.api.model.KristAddress
import io.sc3.kristpay.api.model.KristPayUnallocated
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.PaymentActor
import io.sc3.kristpay.api.model.PaymentResult
import io.sc3.kristpay.core.payment.strategy.KristPaymentStrategy
import io.sc3.kristpay.core.payment.strategy.UnallocatedPaymentStrategy
import io.sc3.kristpay.core.payment.strategy.WalletPaymentStrategy
import io.sc3.kristpay.model.Transaction

interface PaymentStrategy {
    suspend fun debit(amount: MonetaryAmount, pendingTransaction: Transaction, allowDebt: Boolean): PaymentResult
    suspend fun checkPendingDebit(pendingTransaction: Transaction): PaymentResult

    suspend fun credit(amount: MonetaryAmount, pendingTransaction: Transaction): PaymentResult
    suspend fun checkPendingCredit(pendingTransaction: Transaction): PaymentResult

    companion object {
        operator fun get(key: PaymentActor): PaymentStrategy = when (key) {
            is KristAddress -> KristPaymentStrategy(key)
            is KristPayWallet -> WalletPaymentStrategy(key)
            KristPayUnallocated -> UnallocatedPaymentStrategy
        }
    }
}
