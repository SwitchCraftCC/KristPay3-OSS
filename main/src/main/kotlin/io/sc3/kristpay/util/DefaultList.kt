/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

class DefaultList<T>(
    private val source: List<T>,
    private val default: T,
    newSize: Int
): AbstractList<T>() {
    override val size = newSize

    override fun get(index: Int): T {
        return if (index < source.size) source[index] else default
    }
}
