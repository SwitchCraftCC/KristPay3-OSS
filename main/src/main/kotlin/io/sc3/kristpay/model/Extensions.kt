/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.update

fun <ID : Comparable<ID>> Entity<ID>.atomicIncrement(table: IdTable<ID>, column: Column<Int>) {
    table.update({ table.id eq this@atomicIncrement.id }) {
        with(SqlExpressionBuilder) {
            it.update(column, column + 1)
        }
    }

    this.refresh()
}


// Apparently, the database doesn't support the real DISTANT_PAST
val Instant.Companion.NOT_SO_DISTANT_PAST: Instant
    get() = fromEpochMilliseconds(0)
