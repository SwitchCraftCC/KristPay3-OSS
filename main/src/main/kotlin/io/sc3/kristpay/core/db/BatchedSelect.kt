/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.db

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.util.LazyList

fun <ID : Comparable<ID>, T : Entity<ID>, C: Comparable<C>> EntityClass<ID, T>.selectBuffered(
    orderBy: Pair<Column<C>, SortOrder>,
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    batchSize: Int = 100,
): LazyList<T> {
    val whereOp = SqlExpressionBuilder.where()

    require(batchSize > 0) { "Batch size should be greater than 0" }

    val (count, firstPage) = transaction(kpdb) {
        val count = this@selectBuffered.count(whereOp).toInt()
        val firstPage = this@selectBuffered.find(whereOp).orderBy(orderBy).limit(batchSize).toList()

        count to firstPage
    }

    return LazyList(count, firstPage) { landmark, offset, limit ->
        transaction(kpdb) {
            val query = this@selectBuffered.find {
                if (orderBy.second == SortOrder.ASC) {
                    whereOp and (orderBy.first greater landmark.readValues[orderBy.first])
                } else {
                    whereOp and (orderBy.first less landmark.readValues[orderBy.first])
                }
            }
                .limit(limit, offset = offset.toLong())
                .orderBy(orderBy)

            // query.iterator() executes the query
            query.iterator().asSequence().toList()
        }
    }
}
