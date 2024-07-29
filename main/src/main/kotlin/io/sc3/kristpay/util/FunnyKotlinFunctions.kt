/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

fun <T> T.drop() = Unit

fun <E> List<E>.intercalate(of: E): List<E> {
    val list = mutableListOf<E>()
    this.forEachIndexed { index, e ->
        list.add(e)
        if (index != this.size - 1) {
            list.add(of)
        }
    }
    return list
}

inline fun <K, V> MutableMap<K, V>.getOrMaybePut(key: K, defaultValue: () -> V?): V? {
    val value = get(key)
    return if (value == null) {
        val answer = defaultValue()
        if (answer != null) {
            put(key, answer)
        }

        answer
    } else {
        value
    }
}

val unreachable: Nothing get() = throw IllegalStateException("Unreachable code reached")
