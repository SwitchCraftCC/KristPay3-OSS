/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.payment.strategy

import mu.KLogging
import io.sc3.kristpay.api.model.KristPayWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.PaymentResult
import io.sc3.kristpay.core.payment.PaymentStrategy
import io.sc3.kristpay.model.Transaction
import io.sc3.kristpay.model.Wallet

data class WalletPaymentStrategy(private val actor: KristPayWallet): PaymentStrategy {
    override suspend fun debit(amount: MonetaryAmount, pendingTransaction: Transaction, allowDebt: Boolean): PaymentResult {
        val wallet = Wallet.findById(actor.walletId)!!

        if (wallet.balance < amount.amount && !allowDebt) {
            logger.error("Insufficient funds in wallet ${actor.walletId}")
            return PaymentResult.InsufficientFunds
        }

        logger.info("Debiting ${amount.amount} from wallet ${actor.walletId}")
        wallet.balance -= amount.amount
        return PaymentResult.Success
    }

    override suspend fun checkPendingCredit(pendingTransaction: Transaction): PaymentResult {
        throw UnsupportedOperationException("WalletPaymentStrategy does not support pending credits")
    }

    override suspend fun credit(amount: MonetaryAmount, pendingTransaction: Transaction): PaymentResult {
        logger.info("Crediting $amount to Wallet ${actor.walletId}")
        val wallet = Wallet.findById(actor.walletId)!!
        wallet.balance += amount.amount

        return PaymentResult.Success
    }

    override suspend fun checkPendingDebit(pendingTransaction: Transaction): PaymentResult {
        throw UnsupportedOperationException("WalletPaymentStrategy does not support pending debits")
    }

    companion object: KLogging()
}
