/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import org.jetbrains.exposed.dao.id.EntityID

class LastPolledRow(id: EntityID<Int>) : BaseIntEntity(id, LastPolledTable) {
    var lastPolled by LastPolledTable.lastPolled

    companion object : BaseIntEntityClass<LastPolledRow>(LastPolledTable) {
        fun get() = findById(1)
        fun firstSet(value: Int?) = new(1) {
            lastPolled = value
        }
    }
}

// Lol only 1 row
@KristPayModelTable
object LastPolledTable : BaseIntIdTable("last_polled") {
    val lastPolled = integer("last_polled").nullable()
}
