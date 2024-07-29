/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model.util

fun <T, R> List<T>.lazyMap(transform: (T) -> R): List<R> = object : AbstractList<R>() {
    override val size: Int by this@lazyMap::size

    private val transformed = mutableMapOf<Int, R>()
    override fun get(index: Int): R = transformed.getOrPut(index) { transform(this@lazyMap[index]) }
}
