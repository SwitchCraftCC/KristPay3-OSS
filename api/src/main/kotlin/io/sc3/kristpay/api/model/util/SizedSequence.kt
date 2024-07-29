/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model.util

interface SizedSequence<out T>: Sequence<T> {
    val size: Int
}

inline fun <T, R> SizedSequence<T>.mapSized(crossinline transform: (T) -> R): SizedSequence<R> {
    return object : SizedSequence<R> {
        override val size: Int = this@mapSized.size

        override fun iterator(): Iterator<R> = object : Iterator<R> {
            val iterator = this@mapSized.iterator()
            override fun next(): R {
                return transform(iterator.next())
            }

            override fun hasNext(): Boolean {
                return iterator.hasNext()
            }
        }
    }
}
