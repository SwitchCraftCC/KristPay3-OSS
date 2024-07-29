/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist.ws

import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.krist.http.KristHTTP
import io.sc3.kristpay.core.krist.http.KristHTTPException
import io.sc3.kristpay.util.ago
import io.sc3.kristpay.util.startCoroutineTimer
import io.sc3.kristpay.util.supervised
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KLogging
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val WatchdogScope = CoroutineScope(Dispatchers.IO.supervised())
val WebsocketScope = CoroutineScope(Dispatchers.IO.supervised())
object WebsocketManager: KLogging() {
    private val config by CONFIG::krist

    private val lastHeartbeat = AtomicReference(Instant.DISTANT_PAST)
    private val session = AtomicReference<Job?>(null)
    private val starting = AtomicBoolean(false)

    val messageChannel = Channel<String>(256)

    fun start() {
        startWatchdog()
    }

    fun cleanup() {
        logger.info("Cleaning up websocket manager")
        WatchdogScope.cancel()
        WebsocketScope.cancel()
    }

    private fun startWatchdog() = WatchdogScope.startCoroutineTimer(repeatDuration = 15.seconds) {
        if (lastHeartbeat.get() < 2.minutes.ago) {
            // Restart the websocket
            overthrowSession()
        }
    }

    private suspend fun overthrowSession() {
        val gotLock = starting.compareAndSet(false, true)
        if (!gotLock) return

        val newJob = WebsocketScope.launch {
            try {
                logger.info("Establishing connection...")
                val url = KristHTTP.ws.start(config.privateKey).url
                KristHTTP.client.webSocket(url) {
                    val poller = launch { pollMessages() }
                    val writer = launch { writeMessages() }

                    poller.join()
                    writer.cancelAndJoin()
                }
            } catch (e: KristHTTPException) {
                logger.error("Error getting websocket URL: ${e.message}")
            } catch (e: Exception) {
                logger.error("Error establishing websocket connection: ${e.message}")
            } finally {
                starting.set(false)
            }
        }

        session.getAndSet(newJob)?.cancelAndJoin()
    }

    private suspend fun DefaultClientWebSocketSession.pollMessages() {
        for (message in incoming) {
            message as? Frame.Text ?: continue
            lastHeartbeat.set(Clock.System.now())

            val text = message.readText()
            logger.trace("Received message: $text")

            EventManager.fireEvents(text)
        }
    }

    private suspend fun DefaultClientWebSocketSession.writeMessages() {
        for (message in messageChannel) {
            outgoing.send(Frame.Text(message))
        }
    }
}
