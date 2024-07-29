/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import io.sc3.kristpay.api.model.KristAddress
import io.sc3.kristpay.api.model.MasterWallet
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.krist.http.KristHTTP
import io.sc3.kristpay.core.metric.CalculatedGauge
import io.sc3.kristpay.model.Wallets

class MasterWalletImpl: MasterWallet {
    override val address: KristAddress by lazy { KristAddress(CONFIG.krist.address) }

    override val balance: MonetaryAmount by lazy {
        runBlocking {
            MonetaryAmount(KristHTTP.addresses.get(address.address).address.balance)
        }
    }

    override val allocated: MonetaryAmount by lazy {
        MonetaryAmount(transaction(kpdb) {
            val sum = Wallets.balance.sum()
            Wallets.slice(sum).select {
                Wallets.owner neq null // exclude service wallets
            }.first()[sum] ?: 0
        })
    }
}

fun registerMasterWalletMetrics() {
    CalculatedGauge.build()
        .name("kristpay_master_balance")
        .help("The total balance of the master wallet including allocated and unallocated funds.")
        .value { MasterWalletImpl().balance.amount.toDouble() }
        .register()

    CalculatedGauge.build()
        .name("kristpay_master_allocated")
        .help("The amount of krist allocated to wallets")
        .value { MasterWalletImpl().allocated.amount.toDouble() }
        .register()
}
