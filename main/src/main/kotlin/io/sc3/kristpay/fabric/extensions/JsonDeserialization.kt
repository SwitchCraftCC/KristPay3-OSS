/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.extensions

import kotlinx.serialization.json.*

fun JsonObject.getString(key: String) = get(key)?.jsonPrimitive?.contentOrNull
fun JsonObject.getBoolean(key: String) = get(key)?.jsonPrimitive?.booleanOrNull
fun JsonObject.getInt(key: String) = get(key)?.jsonPrimitive?.intOrNull
fun JsonObject.getArray(key: String) = get(key)?.jsonArray
fun JsonObject.getObject(key: String) = get(key)?.jsonObject
