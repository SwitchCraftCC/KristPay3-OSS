/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

import kotlin.reflect.KProperty

class Memo<O: Any, V: Any>(val observer: () -> O, val initializer: () -> V) {
    private lateinit var lastObserved: O
    private lateinit var value: V

    operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
        val currentObserved = observer()
        if (!this::lastObserved.isInitialized || lastObserved != currentObserved) {
            lastObserved = currentObserved
            value = initializer()
        }

        return value
    }
}
