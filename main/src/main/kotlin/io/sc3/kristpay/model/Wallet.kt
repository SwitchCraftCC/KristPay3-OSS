/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.WalletSnapshot
import java.util.*

val walletNameCache = mutableMapOf<UUID, String>()

class Wallet(uuid: EntityID<UUID>) : BaseUUIDEntity(uuid, Wallets) {
    var owner by UserGroup optionalReferencedOn Wallets.owner
    private var _name by Wallets.name
    var name
        get() = _name.also { walletNameCache[id.value] = it }
        set(value) {
            _name = value
            walletNameCache[id.value] = value
        }

    var balance by Wallets.balance
    var lastSeenTransaction by Wallets.lastSeenTransaction

    fun snapshot() = WalletSnapshot(id.value, owner?.id?.value, name, MonetaryAmount(balance), Clock.System.now())

    companion object : EntityClass<UUID, Wallet>(Wallets)
}

@KristPayModelTable
object Wallets: BaseUUIDTable("wallets") {
    val owner = reference("owner", UserGroups).nullable()
    val name = varchar("name", 255).uniqueIndex()
    val balance = integer("balance").default(0)
    val lastSeenTransaction = reference("last_seen_transaction", Transactions).nullable()
}
