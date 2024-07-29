/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Table
import java.util.UUID

@KristPayModelTable
object GroupMemberships: Table() {
    val group = reference("group", UserGroups)
    val member = reference("member", Users)
    override val primaryKey = PrimaryKey(group, member)
}

class UserGroup(id: EntityID<UUID>): BaseUUIDEntity(id, UserGroups) {
    var name by UserGroups.name
    var description by UserGroups.description
    var members by User via GroupMemberships

    val wallets by Wallet optionalReferrersOn Wallets.owner

    companion object : EntityClass<UUID, UserGroup>(UserGroups)
}

@KristPayModelTable
object UserGroups: BaseUUIDTable("groups") {
    val name = varchar("name", 255).uniqueIndex()
    val description = varchar("description", 255).nullable()
}
