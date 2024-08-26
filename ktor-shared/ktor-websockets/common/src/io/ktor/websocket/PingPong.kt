/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.random.*

private val PongerCoroutineName = CoroutineName("ws-ponger")

private val PingerCoroutineName = CoroutineName("ws-pinger")

/**
 * Launch a ponger actor job on the [CoroutineScope] sending pongs to [outgoing] channel.
 * It is acting for every client's ping frame and replying with corresponding pong
 */
internal fun CoroutineScope.ponger(
    outgoing: SendChannel<Frame.Pong>
): SendChannel<Frame.Ping> {
    val channel = Channel<Frame.Ping>(5)

    launch(PongerCoroutineName) {
        try {
            channel.consumeEach {
                LOGGER.trace("Received ping message, sending pong message")
                outgoing.send(Frame.Pong(it.data))
            }
        } catch (_: ClosedSendChannelException) {
        }
    }

    return channel
}

/**
 * Launch pinger coroutine on [CoroutineScope] that is sending ping every specified [periodMillis] to [outgoing] channel,
 * waiting for and verifying client's pong frames. It is also handling [timeoutMillis] and sending timeout close frame
 */

internal fun CoroutineScope.pinger(
    outgoing: SendChannel<Frame>,
    periodMillis: Long,
    timeoutMillis: Long,
    onTimeout: suspend (CloseReason) -> Unit
): SendChannel<Frame.Pong> {
    val actorJob = Job()

    val channel = Channel<Frame.Pong>(Channel.UNLIMITED)

    launch(actorJob + PingerCoroutineName) {
        LOGGER.trace("Starting WebSocket pinger coroutine with period $periodMillis ms and timeout $timeoutMillis ms")
        val random = Random(getTimeMillis())
        val pingIdBytes = ByteArray(32)

        try {
            while (true) {
                // drop pongs during period delay as they are irrelevant
                // here we expect a timeout, so ignore it
                withTimeoutOrNull(periodMillis) {
                    while (true) {
                        channel.receive() // timeout causes loop to break on receive
                    }
                }

                random.nextBytes(pingIdBytes)
                val pingMessage = "[ping ${hex(pingIdBytes)} ping]"

                val rc = withTimeoutOrNull(timeoutMillis) {
                    LOGGER.trace("WebSocket Pinger: sending ping frame")
                    outgoing.send(Frame.Ping(pingMessage.toByteArray(Charsets.ISO_8859_1)))

                    // wait for valid pong message
                    while (true) {
                        val msg = channel.receive()
                        if (msg.data.decodeToString(0, 0 + msg.data.size) == pingMessage) {
                            LOGGER.trace("WebSocket Pinger: received valid pong frame $msg")
                            break
                        }

                        LOGGER.trace("WebSocket Pinger: received invalid pong frame $msg, continue waiting")
                    }
                }

                if (rc == null) {
                    // timeout
                    // we were unable to send the ping or hadn't got a valid pong message in time,
                    // so we are triggering close sequence (if already started then the following close frame could be ignored)

                    LOGGER.trace("WebSocket pinger has timed out")
                    onTimeout(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Ping timeout"))
                    break
                }
            }
        } catch (ignore: CancellationException) {
        } catch (ignore: ClosedReceiveChannelException) {
        } catch (ignore: ClosedSendChannelException) {
        }
    }

    coroutineContext[Job]!!.invokeOnCompletion {
        actorJob.cancel()
    }

    return channel
}
