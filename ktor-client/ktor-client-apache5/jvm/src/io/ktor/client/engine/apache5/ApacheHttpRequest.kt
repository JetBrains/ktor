/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.apache.hc.client5.http.*
import org.apache.hc.client5.http.impl.async.*
import org.apache.hc.core5.concurrent.*
import org.apache.hc.core5.http.message.*
import org.apache.hc.core5.http.nio.*
import java.net.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
internal suspend fun CloseableHttpAsyncClient.sendRequest(
    request: AsyncRequestProducer,
    callContext: CoroutineContext,
    requestData: HttpRequestData
): HttpResponseData {
    val requestTime = GMTDate()

    val bodyConsumer = ApacheResponseConsumer(callContext, requestData)
    val responseConsumer = BasicResponseConsumer(bodyConsumer)

    val callback = object : FutureCallback<Unit> {
        override fun failed(exception: Exception) {}
        override fun completed(result: Unit) {}
        override fun cancelled() {}
    }

    val future = execute(request, responseConsumer, callback)!!
    try {
        val rawResponse = responseConsumer.responseDeferred.await()

        val statusLine = StatusLine(rawResponse)
        val status = HttpStatusCode(statusLine.statusCode, statusLine.reasonPhrase ?: "")
        val version = with(statusLine.protocolVersion) {
            HttpProtocolVersion.fromValue(protocol, major, minor)
        }

        val rawHeaders = rawResponse.headers.filter {
            it.name != null || it.name.isNotBlank()
        }.groupBy(
            { it.name },
            { it.value ?: "" }
        )

        val headers = HeadersImpl(rawHeaders)
        val body: Any = if (requestData.isSseRequest()) {
            DefaultClientSSESession(
                requestData.body as SSEClientContent,
                bodyConsumer.responseChannel,
                callContext,
                status,
                headers
            )
        } else {
            bodyConsumer.responseChannel
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
    exception is ConnectTimeoutException -> ConnectTimeoutException(requestData, exception)
    exception is ConnectException && exception.isTimeoutException() -> ConnectTimeoutException(requestData, exception)
    exception is SocketTimeoutException -> SocketTimeoutException(requestData, exception)
    else -> exception
}
