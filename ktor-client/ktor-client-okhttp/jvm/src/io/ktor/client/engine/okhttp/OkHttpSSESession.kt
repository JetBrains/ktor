/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.sse.*
import kotlin.coroutines.*

internal class OkHttpSSESession(
    engine: OkHttpClient,
    engineRequest: Request,
    override val coroutineContext: CoroutineContext,
) : ClientSSESession, EventSourceListener() {
    private val serverSentEventsSource = EventSources.createFactory(engine).newEventSource(engineRequest, this)

    internal val originResponse: CompletableDeferred<Response> = CompletableDeferred()

    private val _incoming = Channel<ServerSentEvent>(8)

    override val incoming: Flow<ServerSentEvent>
        get() = _incoming.receiveAsFlow()

    override fun onOpen(eventSource: EventSource, response: Response) {
        originResponse.complete(response)
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        _incoming.trySendBlocking(ServerSentEvent(data, type, id))
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        val statusCode = response?.code

        if (response != null && statusCode != HttpStatusCode.OK.value) {
            originResponse.complete(response)
        } else {
            val error = t?.let { SSEException(it) } ?: mapException(response)
            originResponse.completeExceptionally(error)
        }

        _incoming.close()
        serverSentEventsSource.cancel()
    }

    override fun onClosed(eventSource: EventSource) {
        _incoming.close()
        serverSentEventsSource.cancel()
    }

    private fun mapException(response: Response?): SSEException {
        return when {
            response != null && response.code != HttpStatusCode.OK.value ->
                SSEException("Expected status code ${HttpStatusCode.OK.value} but was: ${response.code}")

            response != null && response.headers[HttpHeaders.ContentType]
                ?.let { ContentType.parse(it) }?.withoutParameters() != ContentType.Text.EventStream ->
                SSEException(
                    "Content type must be ${ContentType.Text.EventStream} but was: ${response.headers[HttpHeaders.ContentType]}" // ktlint-disable max-line-length
                )

            else -> SSEException("Unexpected error occurred")
        }
    }
}
