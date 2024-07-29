/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.console

import kotlinx.coroutines.runBlocking
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.KristPayFrontend
import io.sc3.kristpay.api.UserID
import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.api.model.frontend.PaymentRequestResponse
import io.sc3.kristpay.core.KristPay
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.krist.http.KristHTTP
import io.sc3.kristpay.core.krist.ws.WebsocketManager
import io.sc3.kristpay.fabric.extensions.toText
import java.util.UUID

class ConsoleFrontend : KristPayFrontend {
    override fun presentNotification(notification: NotificationSnapshot): Boolean {
        println("NOTIFICATION $notification")
        return true
    }

    override suspend fun presentPaymentRequest(request: ServicePaymentRequest): PaymentRequestResponse {
        TODO("Not yet implemented")
    }

    override fun sendBalance(user: UserID, amount: MonetaryAmount) {
        TODO("Not yet implemented")
    }

    override fun notifyServicePaymentFailure(result: PaymentResult, request: ServicePaymentRequest, response: PaymentRequestResponse) {
        TODO("Not yet implemented")
    }

    override fun notifyServicePaymentSuccess(request: ServicePaymentRequest, response: PaymentRequestResponse) {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("kp.config", "run/config/kristpay.toml")

            val kristPay = KristPay(ConsoleFrontend())

            val commander = ConsoleCommands()
            runBlocking {
                commander.listen()
            }

            kristPay.cleanup()
        }
    }
}

class ConsoleCommands : KristPayConsumer() {
    val me = UUID.fromString("d5448ded-95ca-4de7-b174-e116b6b63eb7")
    val myWallet by lazy { API.getDefaultWallet(me)!! }

    suspend fun listen() {
        while (true) {
            print("KristPay> ")
            val line = readLine() ?: break

            when (line) {
                "exit" -> return
                "bal" -> printBalance()
                "ws" -> KristHTTP.ws.start(CONFIG.krist.privateKey).url.let(::println)
                "msg" -> readLine()?.let { WebsocketManager.messageChannel.send(it) }
                "txs" -> API.listTransactions(myWallet, 1).forEach(::println)
                "money" -> API.initializeTransaction(KristPayUnallocated, KristPayWallet(myWallet), MonetaryAmount(1), initiator = Initiator.Server)
//                "pay" -> printPay()
//                "payments" -> printPayments()
//                "notifications" -> printNotifications()
                else -> println("Unknown command")
            }
        }
    }

    private fun printBalance() {
        println("Balance: ${API.getWalletSnapshot(myWallet)?.balance?.toText()}")
    }
}
