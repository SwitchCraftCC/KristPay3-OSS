/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

import java.security.MessageDigest

internal fun sha256(input: String): String {
    val bytes = input.toByteArray()
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}
