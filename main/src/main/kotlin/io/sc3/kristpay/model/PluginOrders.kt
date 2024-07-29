/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import io.sc3.kristpay.api.model.ServiceProduct
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class PluginOrder(id: EntityID<UUID>) : BaseUUIDEntity(id, PluginOrders) {
    var product by PluginOrders.product.useJSON<ServiceProduct>(PluginOrder)
    var associatedTransaction by Transaction referencedOn PluginOrders.associatedTransaction
    var accepted by PluginOrders.accepted

    companion object : EntityClass<UUID, PluginOrder>(PluginOrders)
}

@KristPayModelTable
object PluginOrders : BaseUUIDTable("plugin_orders") {
    val product = text("product")
    val associatedTransaction = reference("associated_transaction", Transactions)
    val accepted = bool("accepted")
}
