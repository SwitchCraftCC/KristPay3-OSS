/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityChangeType
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.toEntity
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

abstract class BaseIntIdTable(name: String) : IntIdTable(name) {
    val createdAt = timestamp("createdAt").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updatedAt").nullable()
}

abstract class BaseIntEntity(id: EntityID<Int>, table: BaseIntIdTable) : IntEntity(id) {
    val createdAt by table.createdAt
    var updatedAt by table.updatedAt
}

abstract class BaseIntEntityClass<E : BaseIntEntity>(table: BaseIntIdTable) : IntEntityClass<E>(table) {

    init {
        EntityHook.subscribe { action ->
            if (action.changeType == EntityChangeType.Updated) {
                try {
                    action.toEntity(this)?.updatedAt = Clock.System.now()
                } catch (e: Exception) {
                    //nothing much to do here
                }
            }
        }
    }
}

abstract class BaseUUIDTable(name: String) : UUIDTable(name) {
    val createdAt = timestamp("createdAt").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updatedAt").nullable()
}

abstract class BaseUUIDEntity(id: EntityID<UUID>, table: BaseUUIDTable) : UUIDEntity(id) {
    val createdAt by table.createdAt
    var updatedAt by table.updatedAt
}

abstract class BaseUUIDEntityClass<E : BaseUUIDEntity>(table: BaseUUIDTable) : UUIDEntityClass<E>(table) {

    init {
        EntityHook.subscribe { action ->
            if (action.changeType == EntityChangeType.Updated) {
                try {
                    action.toEntity(this)?.updatedAt = Clock.System.now()
                } catch (e: Exception) {
                    //nothing much to do here
                }
            }
        }
    }
}

abstract class BaseIDTable<T : Comparable<T>>(name: String) : IdTable<T>(name) {
    val createdAt = timestamp("createdAt").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updatedAt").nullable()
}

abstract class BaseIDEntity<T : Comparable<T>>(id: EntityID<T>, table: BaseIDTable<T>) : Entity<T>(id) {
    val createdAt by table.createdAt
    var updatedAt by table.updatedAt
}

abstract class BaseIDEntityClass<T : Comparable<T>, E : BaseIDEntity<T>>(table: BaseIDTable<T>) : EntityClass<T, E>(table) {

    init {
        EntityHook.subscribe { action ->
            if (action.changeType == EntityChangeType.Updated) {
                try {
                    action.toEntity(this)?.updatedAt = Clock.System.now()
                } catch (e: Exception) {
                    //nothing much to do here
                }
            }
        }
    }
}

