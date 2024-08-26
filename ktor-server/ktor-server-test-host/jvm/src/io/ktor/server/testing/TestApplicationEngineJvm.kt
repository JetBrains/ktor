/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * Makes a test request that sets up a WebSocket session and invokes the [callback] function
 * that handles conversation with the server
 */
public fun TestApplicationEngine.handleWebSocketConversation(
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {},
    awaitCallback: Boolean = true,
    callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
): TestApplicationCall {
    return runBlocking {
        handleWebSocketConversationNonBlocking(uri, setup, awaitCallback, callback)
    }
}

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun TestApplicationEngine.handleWebSocketConversationNonBlocking(
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {},
    awaitCallback: Boolean = true,
    callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
): TestApplicationCall {
    val websocketChannel = ByteChannel(true)
    val call = createWebSocketCall(uri) {
        setup()
        bodyChannel = websocketChannel
    }

    // we need this to wait for response channel appearance
    // otherwise we get NPE at websocket reader start attempt
    val responseSent: CompletableJob = Job()
    call.response.responseChannelDeferred.invokeOnCompletion { cause ->
        when (cause) {
            null -> responseSent.complete()
            else -> responseSent.completeExceptionally(cause)
        }
    }

    this.launch(configuration.dispatcher) {
        try {
            // execute server-side
            pipeline.execute(call)
        } catch (t: Throwable) {
            responseSent.completeExceptionally(t)
        }
    }

    val pool = KtorDefaultPool
    val engineContext = Dispatchers.Unconfined
    val job = Job()
    val webSocketContext = engineContext + job

    withContext(configuration.dispatcher) {
        responseSent.join()
        processResponse(call)
        val connectionEstablished = withTimeoutOrNull(1000) {
            call.response.webSocketEstablished.join()
        }
        if (connectionEstablished == null) {
            job.cancel()
            throw IllegalStateException("WebSocket connection failed")
        }

        val writer = WebSocketWriter(websocketChannel, webSocketContext, pool = pool)
        val responseChannel = call.response.websocketChannel()
            ?: error("Expected websocket channel in the established connection")
        val reader = WebSocketReader(responseChannel, webSocketContext, Int.MAX_VALUE.toLong(), pool)

        val scope = if (awaitCallback) this else GlobalScope
        scope.launch {
            try {
                // execute client side
                call.callback(reader.incoming, writer.outgoing)
            } finally {
                writer.flush()
                writer.outgoing.close()
                job.cancelAndJoin()
            }
        }
    }

    return call
}
