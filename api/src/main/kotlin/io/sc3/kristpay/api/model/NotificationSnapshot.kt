/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api.model

import java.util.UUID

data class NotificationSnapshot(
    val user: UUID,
    val referenceTransaction: TransactionSnapshot
)
