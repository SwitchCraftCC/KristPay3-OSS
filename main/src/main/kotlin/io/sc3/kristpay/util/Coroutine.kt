/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import mu.KLogging

val logger = KLogging().logger
fun CoroutineDispatcher.supervised() = this + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
    logger.error(throwable) { "Uncaught coroutine exception: " }
}
