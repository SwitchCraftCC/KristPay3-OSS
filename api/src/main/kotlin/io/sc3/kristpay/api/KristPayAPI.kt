/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api

import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.api.model.frontend.TransactionSymbol
import kotlinx.serialization.json.JsonObject
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

typealias UserID = UUID
typealias WalletID = UUID
typealias UserGroupID = UUID

interface KristPayAPI {

    fun acquireServiceWallet(id: String): WalletID

    suspend fun planServicePayment(request: ServicePaymentRequest): ServicePaymentPlan?
    fun subscribeToPaymentGroup(pluginID: PluginID, groupID: GroupID, callback: (ServicePayment) -> Unit)

    /** Callback should return a TransactionSymbol object, or null if it doesn't want to handle the transaction. */
    fun registerTransactionSymbol(callback: (KristPayWallet, TransactionSnapshot) -> TransactionSymbol?)
    /** Callback should return a list of Minecraft Text objects, serialised to JSON, or null if it doesn't want to
     * handle the transaction. */
    fun registerTransactionDescriptor(callback: (KristPayWallet, TransactionSnapshot) -> List<String>?)

    fun getMasterWallet(): MasterWallet

    fun getWalletSnapshot(wallet: WalletID): WalletSnapshot?
    fun getWalletName(wallet: WalletID): String?

    fun findWalletByName(name: String): WalletSnapshot?

    fun getDefaultWallet(userID: UserID): WalletID?
    fun getDefaultWalletSnapshot(userID: UserID): WalletSnapshot? = getDefaultWallet(userID)?.let { getWalletSnapshot(it) }

    fun getNotifications(userID: UserID): List<NotificationSnapshot>
    fun clearNotifications(userID: UserID)

    // True if user was created, False if user already existed
    fun initializeUser(userID: UserID, userReferenceName: String, giveStartingBalance: Boolean = true): Boolean

    fun initializeTransaction(
        from: PaymentActor,
        to: PaymentActor,
        amount: MonetaryAmount,
        metadata: String? = null,
        initiator: Initiator,
        systemMetadata: JsonObject? = null,
        sendNotification: Boolean = true,
        externalReference: Int? = null,
        allowDebt: Boolean = false
    ): PaymentResult

    // The list returned is lazy, and will only fetch more pages as needed
    // Because of this, never fully iterate over the list unless you're sure you want to
    // Use lazyMap to transform the list when necessary
    fun listTransactions(
        wallet: WalletID,
        pageSize: Int = 100,
    ): List<TransactionSnapshot>

    fun getTransaction(id: UUID): TransactionSnapshot?

    fun getFrontend(): KristPayFrontend

    @Internal
    fun cleanup()
}
