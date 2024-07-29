/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import org.jetbrains.exposed.dao.id.EntityID
import io.sc3.kristpay.api.model.NotificationSnapshot

class Notification(id: EntityID<Int>): BaseIntEntity(id, Notifications) {
    var user by User referencedOn Notifications.user
    var referenceTransaction by Transaction referencedOn Notifications.referenceTransaction

    fun toSnapshot(): NotificationSnapshot
        = NotificationSnapshot(
            user = user.id.value,
            referenceTransaction = referenceTransaction.toSnapshot()
        )

    companion object : BaseIntEntityClass<Notification>(Notifications)
}

@KristPayModelTable
object Notifications : BaseIntIdTable("notifications") {
    val user = reference("user", Users).index()
    val referenceTransaction = reference("reference_transaction", Transactions)
}
