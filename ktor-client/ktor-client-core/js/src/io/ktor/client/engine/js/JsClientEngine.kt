/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.client.engine.js.compatibility.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.coroutines.*

internal class JsClientEngine(
    override val config: JsClientEngineConfig,
) : HttpClientEngineBase("ktor-js") {

    override val supportedCapabilities = setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    init {
        check(config.proxy == null) { "Proxy unsupported in Js engine." }
    }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val clientConfig = data.attributes[CLIENT_CONFIG]

        if (data.isUpgradeRequest()) {
            return executeWebSocketRequest(data, callContext)
        }

        val requestTime = GMTDate()
        val rawRequest = data.toRaw(clientConfig, callContext)
        val rawResponse = commonFetch(data.url.toString(), rawRequest, config)

        val status = HttpStatusCode(rawResponse.status.toInt(), rawResponse.statusText)
        val headers = rawResponse.headers.mapToKtor()
        val version = HttpProtocolVersion.HTTP_1_1

        val body = CoroutineScope(callContext).readBody(rawResponse)
        val responseBody: Any = if (needToProcessSSE(data, status, headers)) {
            DefaultClientSSESession(data.body as SSEClientContent, body, callContext)
        } else {
            body
        }

        return HttpResponseData(
            status,
            requestTime,
            headers,
            version,
            responseBody,
            callContext
        )
    }

    // Adding "_capturingHack" to reduce chances of JS IR backend to rename variable,
    // so it can be accessed inside js("") function
    @Suppress("UNUSED_PARAMETER", "UnsafeCastFromDynamic", "UNUSED_VARIABLE", "LocalVariableName")
    private fun createWebSocket(
        urlString_capturingHack: String,
        headers: Headers
    ): WebSocket {
        val protocolHeaderNames = headers.names().filter { headerName ->
            headerName.equals("sec-websocket-protocol", true)
        }
        val protocols = protocolHeaderNames.mapNotNull { headers.getAll(it) }.flatten().toTypedArray()
        return when (PlatformUtils.platform) {
            Platform.Browser -> js("new WebSocket(urlString_capturingHack, protocols)")
            else -> {
                val ws_capturingHack = js("eval('require')('ws')")
                val headers_capturingHack: dynamic = object {}
                headers.forEach { name, values ->
                    headers_capturingHack[name] = values.joinToString(",")
                }
                js("new ws_capturingHack(urlString_capturingHack, protocols, { headers: headers_capturingHack })")
            }
        }
    }

    private suspend fun executeWebSocketRequest(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        val requestTime = GMTDate()

        val urlString = request.url.toString()
        val socket: WebSocket = createWebSocket(urlString, request.headers)

        try {
            socket.awaitConnection()
        } catch (cause: Throwable) {
            callContext.cancel(CancellationException("Failed to connect to $urlString", cause))
            throw cause
        }

        val session = JsWebSocketSession(callContext, socket)

        return HttpResponseData(
            HttpStatusCode.SwitchingProtocols,
            requestTime,
            Headers.Empty,
            HttpProtocolVersion.HTTP_1_1,
            session,
            callContext
        )
    }
}

private suspend fun WebSocket.awaitConnection(): WebSocket = suspendCancellableCoroutine { continuation ->
    if (continuation.isCancelled) return@suspendCancellableCoroutine

    val eventListener = { event: Event ->
        when (event.type) {
            "open" -> continuation.resume(this)
            "error" -> {
                continuation.resumeWithException(WebSocketException(event.asString()))
            }
        }
    }

    addEventListener("open", callback = eventListener)
    addEventListener("error", callback = eventListener)

    continuation.invokeOnCancellation {
        removeEventListener("open", callback = eventListener)
        removeEventListener("error", callback = eventListener)

        if (it != null) {
            this@awaitConnection.close()
        }
    }
}

private fun Event.asString(): String = buildString {
    append(JSON.stringify(this@asString, arrayOf("message", "target", "type", "isTrusted")))
}

private fun org.w3c.fetch.Headers.mapToKtor(): Headers = buildHeaders {
    this@mapToKtor.asDynamic().forEach { value: String, key: String ->
        append(key, value)
    }

    Unit
}

/**
 * Wrapper for javascript `error` objects.
 * @property origin: fail reason
 */
@Suppress("MemberVisibilityCanBePrivate")
public class JsError(public val origin: dynamic) : Throwable("Error from javascript[$origin].")
