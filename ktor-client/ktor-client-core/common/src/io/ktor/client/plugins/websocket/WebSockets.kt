/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.websocket

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.websocket.*

private val REQUEST_EXTENSIONS_KEY = AttributeKey<List<WebSocketExtension<*>>>("Websocket extensions")

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.websocket.WebSockets")

/**
 * Indicates if a client engine supports WebSockets.
 */
public object WebSocketCapability : HttpClientEngineCapability<Unit> {
    override fun toString(): String = "WebSocketCapability"
}

/**
 * Indicates if a client engine supports extensions for WebSocket plugin.
 */
public object WebSocketExtensionsCapability : HttpClientEngineCapability<Unit> {
    override fun toString(): String = "WebSocketExtensionsCapability"
}

/**
 * Client WebSocket plugin.
 *
 * @property pingInterval - interval between [FrameType.PING] messages.
 * @property maxFrameSize - max size of a single websocket frame.
 * @property extensionsConfig - extensions configuration
 * @property contentConverter - converter for serialization/deserialization
 */
public class WebSockets internal constructor(
    public val pingInterval: Long,
    public val maxFrameSize: Long,
    private val extensionsConfig: WebSocketExtensionsConfig,
    public val contentConverter: WebsocketContentConverter? = null
) {
    /**
     * Client WebSocket plugin.
     *
     * @property pingInterval - interval between [FrameType.PING] messages.
     * @property maxFrameSize - max size of single websocket frame.
     */
    public constructor(
        pingInterval: Long = -1L,
        maxFrameSize: Long = Int.MAX_VALUE.toLong()
    ) : this(pingInterval, maxFrameSize, WebSocketExtensionsConfig())

    /**
     * Client WebSocket plugin.
     */
    public constructor() : this(-1L, Int.MAX_VALUE.toLong(), WebSocketExtensionsConfig())

    private fun installExtensions(context: HttpRequestBuilder) {
        val installed = extensionsConfig.build()
        context.attributes.put(REQUEST_EXTENSIONS_KEY, installed)

        val protocols = installed.flatMap { it.protocols }
        addNegotiatedProtocols(context, protocols)
    }

    @Suppress("UNCHECKED_CAST")
    private fun completeNegotiation(
        call: HttpClientCall
    ): List<WebSocketExtension<*>> {
        val serverExtensions: List<WebSocketExtensionHeader> = call.response
            .headers[HttpHeaders.SecWebSocketExtensions]
            ?.let { parseWebSocketExtensions(it) } ?: emptyList()

        val clientExtensions = call.attributes[REQUEST_EXTENSIONS_KEY]

        return clientExtensions.filter { it.clientNegotiation(serverExtensions) }
    }

    private fun addNegotiatedProtocols(context: HttpRequestBuilder, protocols: List<WebSocketExtensionHeader>) {
        if (protocols.isEmpty()) return

        val headerValue = protocols.joinToString(";")
        context.header(HttpHeaders.SecWebSocketExtensions, headerValue)
    }

    internal fun convertSessionToDefault(session: WebSocketSession): DefaultWebSocketSession {
        if (session is DefaultWebSocketSession) return session

        return DefaultWebSocketSession(session, pingInterval, timeoutMillis = pingInterval * 2).also {
            it.maxFrameSize = this@WebSockets.maxFrameSize
        }
    }

    /**
     * [WebSockets] configuration.
     */
    @KtorDsl
    public class Config {
        internal val extensionsConfig: WebSocketExtensionsConfig = WebSocketExtensionsConfig()

        /**
         * Sets interval of sending ping frames.
         *
         * Value -1L is for disabled ping.
         */
        public var pingInterval: Long = -1L

        /**
         * Sets maximum frame size in bytes.
         */
        public var maxFrameSize: Long = Int.MAX_VALUE.toLong()

        /**
         * A converter for serialization/deserialization
         */
        public var contentConverter: WebsocketContentConverter? = null

        /**
         * Configure WebSocket extensions.
         */
        public fun extensions(block: WebSocketExtensionsConfig.() -> Unit) {
            extensionsConfig.apply(block)
        }
    }

    /**
     * Add WebSockets support for ktor http client.
     */
    public companion object Plugin : HttpClientPlugin<Config, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("Websocket")

        override fun prepare(block: Config.() -> Unit): WebSockets {
            val config = Config().apply(block)
            return WebSockets(
                config.pingInterval,
                config.maxFrameSize,
                config.extensionsConfig,
                config.contentConverter
            )
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: WebSockets, scope: HttpClient) {
            val extensionsSupported = scope.engine.supportedCapabilities.contains(WebSocketExtensionsCapability)

            scope.requestPipeline.intercept(HttpRequestPipeline.Render) {
                if (!context.url.protocol.isWebsocket()) {
                    LOGGER.trace("Skipping WebSocket plugin for non-websocket request: ${context.url}")
                    return@intercept
                }

                LOGGER.trace("Sending WebSocket request ${context.url}")
                context.setCapability(WebSocketCapability, Unit)

                if (extensionsSupported) {
                    plugin.installExtensions(context)
                }

                proceedWith(WebSocketContent())
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, session) ->
                val response = this.context.response
                val status = response.status
                val requestContent = response.request.content

                if (requestContent !is WebSocketContent) {
                    LOGGER.trace("Skipping non-websocket response from ${context.request.url}: $session")
                    return@intercept
                }
                if (status != HttpStatusCode.SwitchingProtocols) {
                    throw WebSocketException(
                        "Handshake exception, expected status code ${HttpStatusCode.SwitchingProtocols.value} but was ${status.value}" // ktlint-disable max-line-length
                    )
                }
                if (session !is WebSocketSession) {
                    throw WebSocketException(
                        "Handshake exception, expected `WebSocketSession` content but was $session"
                    )
                }

                LOGGER.trace("Receive websocket session from ${context.request.url}: $session")
                val clientSession: ClientWebSocketSession = when (info.type) {
                    DefaultClientWebSocketSession::class -> {
                        val defaultSession = plugin.convertSessionToDefault(session)
                        val clientSession = DefaultClientWebSocketSession(context, defaultSession)

                        val negotiated = if (extensionsSupported) {
                            plugin.completeNegotiation(context)
                        } else {
                            emptyList()
                        }

                        clientSession.apply {
                            start(negotiated)
                        }
                    }

                    else -> DelegatingClientWebSocketSession(context, session)
                }

                proceedWith(HttpResponseContainer(info, clientSession))
            }
        }
    }
}

@Suppress("KDocMissingDocumentation")
public class WebSocketException(message: String, cause: Throwable?) : IllegalStateException(message, cause) {
    // required for backwards binary compatibility
    public constructor(message: String) : this(message, cause = null)
}
