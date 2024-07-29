/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.api

import io.sc3.kristpay.api.model.MonetaryAmount
import io.sc3.kristpay.api.model.NotificationSnapshot
import io.sc3.kristpay.api.model.PaymentResult
import io.sc3.kristpay.api.model.ServicePaymentRequest
import io.sc3.kristpay.api.model.frontend.PaymentRequestResponse

interface KristPayFrontend {

    suspend fun presentPaymentRequest(request: ServicePaymentRequest): PaymentRequestResponse

    fun presentNotification(notification: NotificationSnapshot): Boolean

    fun sendBalance(user: UserID, amount: MonetaryAmount)

    fun notifyServicePaymentSuccess(request: ServicePaymentRequest, response: PaymentRequestResponse)
    fun notifyServicePaymentFailure(result: PaymentResult, request: ServicePaymentRequest, response: PaymentRequestResponse)
}
