/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.apache.http.concurrent.*
import org.apache.http.impl.nio.client.*
import java.net.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
internal suspend fun CloseableHttpAsyncClient.sendRequest(
    request: ApacheRequestProducer,
    callContext: CoroutineContext,
    requestData: HttpRequestData
): HttpResponseData {
    val requestTime = GMTDate()

    val consumer = ApacheResponseConsumer(callContext, requestData)

    val callback = object : FutureCallback<Unit> {
        override fun failed(exception: Exception) {}
        override fun completed(result: Unit) {}
        override fun cancelled() {}
    }

    val future = execute(request, consumer, callback)!!
    try {
        val rawResponse = consumer.waitForResponse()
        val statusLine = rawResponse.statusLine

        val status = HttpStatusCode(statusLine.statusCode, statusLine.reasonPhrase)
        val version = with(rawResponse.protocolVersion) {
            HttpProtocolVersion.fromValue(protocol, major, minor)
        }

        val rawHeaders = rawResponse.allHeaders.filter {
            it.name != null || !it.name.isBlank()
        }.groupBy(
            { it.name },
            { it.value ?: "" }
        )

        val headers = HeadersImpl(rawHeaders)

        val body: Any = if (requestData.isSseRequest()) {
            DefaultClientSSESession(
                requestData.body as SSEClientContent,
                consumer.responseChannel,
                callContext,
                status,
                headers
            )
        } else {
            consumer.responseChannel
        }
        return HttpResponseData(status, requestTime, headers, version, body, callContext)
    } catch (cause: Exception) {
        future.cancel(true)
        val mappedCause = mapCause(cause, requestData)
        callContext.cancel(CancellationException("Failed to execute request.", mappedCause))
        throw mappedCause
    }
}

internal fun mapCause(exception: Exception, requestData: HttpRequestData): Exception = when {
    exception is ConnectException && exception.isTimeoutException() -> ConnectTimeoutException(requestData, exception)
    exception is java.net.SocketTimeoutException -> SocketTimeoutException(requestData, exception)
    else -> exception
}
