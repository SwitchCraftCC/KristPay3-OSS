/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.model

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import io.sc3.kristpay.api.model.Initiator
import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.PaymentActor
import io.sc3.kristpay.api.model.TransactionSnapshot
import io.sc3.kristpay.api.model.TransactionState
import java.util.UUID

class Transaction(id: EntityID<UUID>) : BaseUUIDEntity(id, Transactions) {
    var externalReference by Transactions.externalReference

    var state by Transactions.state
    var attempt by Transactions.attempt

    var from by Transactions.from.transform(PaymentActor::serialize, PaymentActor::deserialize)
    var to by Transactions.to.transform(PaymentActor::serialize, PaymentActor::deserialize)
    var amount by Transactions.amount

    var initiator by Transactions.initiator.transform(Initiator::serialize, Initiator::deserialize)
    var metadata by Transactions.metadata
    var systemMetadata by Transactions.systemMetadata.useNullableJSON<JsonObject>(Transaction)
    var notify by Transactions.notify

    fun toSnapshot(): TransactionSnapshot = TransactionSnapshot(
        id.value, state, from, to, MonetaryAmount(amount), initiator, metadata, systemMetadata, externalReference, notify, createdAt
    )

    companion object : UUIDEntityClass<Transaction>(Transactions)
}

@KristPayModelTable
object Transactions : BaseUUIDTable("transactions") {
    val externalReference = integer("external_reference").nullable().index()

    val state = enumerationByName("state", 63, TransactionState::class).default(TransactionState.INIT)
    val attempt = integer("attempt").nullable()

    val from = varchar("from", 255).index()
    val to = varchar("to", 255).index()
    val amount = integer("amount")

    val initiator = varchar("initiator", 255)
    val metadata = text("metadata").nullable()
    val systemMetadata = text("system_metadata").nullable()
    val notify = bool("notify").default(true)
}

class InflightTransaction(id: EntityID<UUID>) : BaseUUIDEntity(id, InflightTransactions) {
    var transaction: Transaction by Transaction referencedOn InflightTransactions.transaction

    companion object : UUIDEntityClass<InflightTransaction>(InflightTransactions)
}

@KristPayModelTable
object InflightTransactions : BaseUUIDTable("inflight_transactions") {
    val transaction = reference("transaction", Transactions)
}
