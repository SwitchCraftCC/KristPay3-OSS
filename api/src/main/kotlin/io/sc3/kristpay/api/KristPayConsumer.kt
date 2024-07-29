/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api

abstract class KristPayConsumer {
    protected val API: KristPayAPI by lazy { KristPayProvider.get() }
}
