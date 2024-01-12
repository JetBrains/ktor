/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugin.tracing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*

/**
 * Engine with tracer that wraps request execution into tracing functionality. Calls correspondent [tracer] method on
 * every important processing event.
 */
@InternalAPI
class EngineWithTracer(
    private val delegate: HttpClientEngine,
    private val tracer: Tracer
) : HttpClientEngine by delegate {

    private val sequence = atomic(0)

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val requestId = "${sequence.getAndIncrement()}"
        data.traceRequestWillBeSent(requestId)

        try {
            val result = delegate.execute(data)
            data.tracerHeadersReceived(requestId, result)

            coroutineContext[Job]!!.invokeOnCompletion {
                data.traceResponseReadFinished(requestId)
            }

            return with(result) {
                HttpResponseData(
                    statusCode,
                    requestTime,
                    headers,
                    version,
                    data.transformBody(requestId, result),
                    callContext
                )
            }
        } catch (cause: Throwable) {
            tracer.httpExchangeFailed(requestId, cause.message!!)
            throw cause
        }
    }

    @InternalAPI
    override fun install(client: HttpClient) {
        super.install(client)
    }

    private fun HttpRequestData.transformBody(requestId: String, result: HttpResponseData): Any =
        if (body is ClientUpgradeContent) {
            WebSocketSessionTracer(
                requestId,
                tracer,
                result.body as DefaultWebSocketSession
            )
        } else {
            tracer.interpretResponse(
                requestId,
                headers[HttpHeaders.ContentType],
                headers[HttpHeaders.ContentEncoding],
                result.body
            )!!
        }

    private fun HttpRequestData.traceRequestWillBeSent(requestId: String) {
        if (body is ClientUpgradeContent) {
            tracer.webSocketCreated(requestId, url.toString())
            tracer.webSocketWillSendHandshakeRequest(requestId, this)
        } else {
            tracer.requestWillBeSent(requestId, this)
        }
    }

    private fun HttpRequestData.tracerHeadersReceived(requestId: String, result: HttpResponseData) {
        if (body is ClientUpgradeContent) {
            tracer.webSocketHandshakeResponseReceived(requestId, this, result)
        } else {
            tracer.responseHeadersReceived(requestId, this, result)
        }
    }

    private fun HttpRequestData.traceResponseReadFinished(requestId: String) {
        if (body is ClientUpgradeContent) {
            tracer.webSocketClosed(requestId)
        } else {
            tracer.responseReadFinished(requestId)
        }
    }
}
