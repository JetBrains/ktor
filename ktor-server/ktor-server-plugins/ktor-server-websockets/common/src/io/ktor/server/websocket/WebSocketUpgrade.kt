/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.websocket

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.http.websocket.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * An [OutgoingContent] response object that could be used to `respond()`: it will cause application engine to
 * perform HTTP upgrade and start websocket RAW session.
 *
 * Please note that you generally shouldn't use this object directly but use [WebSockets] plugin with routing builders
 * [webSocket] instead.
 *
 * [handle] function is applied to a session and as far as it is a RAW session, you should handle all low-level
 * frames yourself and deal with ping/pongs, timeouts, close frames, frame fragmentation and so on.
 *
 * @param call that is starting web socket session
 * @param protocol web socket negotiated protocol name (optional)
 * @param installExtensions specifies if WebSocket extensions should be installed in current session.
 * @param handle function that is started once HTTP upgrade complete and the session will end once this function exit
 */
public class WebSocketUpgrade(
    public val call: ApplicationCall,
    @Suppress("MemberVisibilityCanBePrivate") public val protocol: String? = null,
    private val installExtensions: Boolean = false,
    public val handle: suspend WebSocketSession.() -> Unit
) : OutgoingContent.ProtocolUpgrade() {

    /**
     * An [OutgoingContent] response object that could be used to `respond()`: it will cause application engine to
     * perform HTTP upgrade and start websocket RAW session.
     *
     * Please note that you generally shouldn't use this object directly but use [WebSockets] plugin with routing builders
     * [webSocket] instead.
     *
     * [handle] function is applied to a session and as far as it is a RAW session, you should handle all low-level
     * frames yourself and deal with ping/pongs, timeouts, close frames, frame fragmentation and so on.
     *
     * @param call that is starting web socket session
     * @param protocol web socket negotiated protocol name (optional)
     * @param handle function that is started once HTTP upgrade complete and the session will end once this function exit
     */
    @Suppress("unused")
    public constructor(
        call: ApplicationCall,
        protocol: String? = null,
        handle: suspend WebSocketSession.() -> Unit
    ) : this(call, protocol, installExtensions = false, handle)

    private val key = call.request.header(HttpHeaders.SecWebSocketKey)
    private val plugin = call.application.plugin(WebSockets)

    override val headers: Headers

    init {
        headers = Headers.build {
            append(HttpHeaders.Upgrade, "websocket")
            append(HttpHeaders.Connection, "Upgrade")
            if (key != null) {
                append(HttpHeaders.SecWebSocketAccept, websocketServerAccept(key))
            }
            if (protocol != null) {
                append(HttpHeaders.SecWebSocketProtocol, protocol)
            }

            val extensionsToUse = writeExtensions()
            call.attributes.put(WebSockets.EXTENSIONS_KEY, extensionsToUse)
        }
    }

    override suspend fun upgrade(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    ): Job {
        val webSocket = RawWebSocket(
            input,
            output,
            plugin.maxFrameSize,
            plugin.masking,
            coroutineContext = engineContext + (coroutineContext[Job] ?: EmptyCoroutineContext)
        )

        webSocket.launch(WebSocketHandlerCoroutineName) {
            try {
                webSocket.handle()
                webSocket.flush()
            } catch (cause: Throwable) {
                webSocket.cancel("WebSocket is cancelled", cause)
            } finally {
                webSocket.cancel()
            }
        }

        return webSocket.coroutineContext[Job]!!
    }

    private fun HeadersBuilder.writeExtensions(): List<WebSocketExtension<*>> {
        if (!installExtensions) return emptyList()

        val requestedExtensions = call.request.header(HttpHeaders.SecWebSocketExtensions)
            ?.let { parseWebSocketExtensions(it) } ?: emptyList()

        val extensionsCandidates = plugin.extensionsConfig.build()
        val extensionHeaders = mutableListOf<WebSocketExtensionHeader>()
        val extensionsToUse = mutableListOf<WebSocketExtension<*>>()

        extensionsCandidates.forEach {
            val headers = it.serverNegotiation(requestedExtensions)
            if (headers.isEmpty()) return@forEach

            extensionsToUse.add(it)
            extensionHeaders.addAll(headers)
        }

        if (extensionHeaders.isNotEmpty()) {
            append(HttpHeaders.SecWebSocketExtensions, extensionHeaders.joinToString(";"))
        }

        return extensionsToUse
    }

    public companion object {
        private val WebSocketHandlerCoroutineName = CoroutineName("raw-ws-handler")
    }
}
