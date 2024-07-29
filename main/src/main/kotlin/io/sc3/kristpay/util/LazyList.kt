/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

class LazyList<T>(
    override val size: Int,
    firstPage: List<T>,
    private val supplier: (landmark: T, offset: Int, limit: Int) -> List<T>
): AbstractList<T>() {
    private val pageSize: Int = firstPage.size

    private val cache = mutableMapOf<Int, T>().apply {
        firstPage.forEachIndexed { index, t -> put(index, t) }
    }

    override fun get(index: Int): T {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for list of size $size")
        }

        if (index in cache) {
            return cache[index]!!
        }

        val page = index / pageSize
        val (landmark, landmarkIndex) = run {
            var i = page
            while (i >= 0) {
                val landmarkIndex = i * pageSize + pageSize - 1
                if (cache.containsKey(landmarkIndex)) {
                    return@run Pair(cache[landmarkIndex]!!, landmarkIndex)
                }

                i--
            }

            throw IllegalStateException("No landmark found")
        }

        // Offset is the number of items to skip from the landmark
        val offset = page * pageSize - landmarkIndex - 1

        val items = supplier(landmark, offset, pageSize)
        items.forEachIndexed { i, item ->
            cache[(page * pageSize) + i] = item
        }

        return cache[index]!!
    }

}
