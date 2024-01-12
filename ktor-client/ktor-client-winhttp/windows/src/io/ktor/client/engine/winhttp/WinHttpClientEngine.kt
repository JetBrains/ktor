/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.engine.*
import io.ktor.client.engine.winhttp.internal.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class WinHttpClientEngine(
    override val config: WinHttpClientEngineConfig
) : HttpClientEngineBase("ktor-winhttp") {

    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override val supportedCapabilities = setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    private val session = WinHttpSession(config)

    init {
        coroutineContext.job.invokeOnCompletion {
            session.close()
        }
    }

    override fun toString(): String = "WinHttp"

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()
        val request = session.createRequest(data)

        callContext.job.invokeOnCompletion {
            request.close()
        }

        val requestProducer = WinHttpRequestProducer(request, data)
        val headers = requestProducer.getHeaders()

        if (data.isUpgradeRequest()) {
            request.upgradeToWebSocket()
        }

        request.sendRequest(headers)
        requestProducer.writeBody()

        val rawResponse = request.getResponse()
        val responseBody: Any = if (data.isUpgradeRequest()) {
            request.createWebSocket(callContext)
        } else {
            request.readBody(callContext)
        }

        return rawResponse.convert(data, requestTime, responseBody, callContext)
    }
}
