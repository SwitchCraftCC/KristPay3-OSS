/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.sql.Column
import java.util.*

inline fun <reified TReal> Column<String>.useJSON(entity: EntityClass<*, *>) = with(entity) {
    this@useJSON.transform(
        { Json.encodeToString(serializer(), it) },
        { Json.decodeFromString(serializer<TReal>(), it) }
    )
}

inline fun <reified TReal> Column<String?>.useNullableJSON(entity: EntityClass<*, *>) = with(entity) {
    this@useNullableJSON.transform(
        { it?.let { Json.encodeToString(serializer(), it) } },
        { it?.let { Json.decodeFromString(serializer<TReal>(), it) } }
    )
}

fun String.toUUID(): UUID = UUID.fromString(this)
