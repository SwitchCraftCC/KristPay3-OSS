/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class User(uuid: EntityID<UUID>) : BaseUUIDEntity(uuid, Users) {
    var groups by UserGroup via GroupMemberships
    var defaultWallet by Wallet referencedOn Users.defaultWallet
    var defaultWalletId by Users.defaultWallet

    companion object : EntityClass<UUID, User>(Users)
}

@KristPayModelTable
object Users: BaseUUIDTable("users") {
    val defaultWallet = reference("default_wallet", Wallets)
}
