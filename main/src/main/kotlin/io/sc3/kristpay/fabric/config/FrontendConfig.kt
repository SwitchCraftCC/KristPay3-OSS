/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.config

import kotlinx.serialization.Serializable

@Serializable
data class FrontendConfig(
    val currencySymbol: String = "KST",
    val payWarningThreshold: Long = 1000,

    val style: Style = Style(),
)


