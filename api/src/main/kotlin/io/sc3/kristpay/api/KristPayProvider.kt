/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api

import org.jetbrains.annotations.ApiStatus.Internal

object KristPayProvider {
    private var instance: KristPayAPI? = null

    fun get() = instance ?: throw NotLoadedException

    @Internal
    fun register(kristPay: KristPayAPI) {
        instance = kristPay
    }

    @Internal
    fun unregister() {
        instance = null
    }

    private object NotLoadedException : IllegalStateException() {
        private const val MESSAGE = "The KristPay API isn't loaded yet!\n" +
                "This could be because:\n" +
                "  a) the KristPay plugin is not installed or it failed to enable\n" +
                "  b) the plugin in the stacktrace does not declare a dependency on KristPay\n" +
                "  c) the plugin in the stacktrace is retrieving the API before the plugin 'enable' phase\n" +
                "     (call the #get method in onEnable, not the constructor!)\n"
    }
}
