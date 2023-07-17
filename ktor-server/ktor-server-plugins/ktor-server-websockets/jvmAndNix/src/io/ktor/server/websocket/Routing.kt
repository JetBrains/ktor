/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.websocket

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

/**
 * Binds RAW WebSocket at the current route + [path] optionally checking the for WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession.incoming] channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSockets
 * it is important to perform close sequence properly.
 */
public fun RoutingBuilder.webSocketRaw(
    path: String,
    protocol: String? = null,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    webSocketRaw(path, protocol, negotiateExtensions = false, handler)
}

/**
 * Bind RAW WebSocket at the current route + [path] optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession.incoming] channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSockets
 * it is important to perform close sequence properly.
 *
 * @param negotiateExtensions indicates if the server should negotiate installed WebSocket extensions.
 */
public fun RoutingBuilder.webSocketRaw(
    path: String,
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    plugin(WebSockets) // early require

    route(path, HttpMethod.Get) {
        webSocketRaw(protocol, negotiateExtensions, handler)
    }
}

/**
 * Bind RAW WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession]'s incoming channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSocket
 * it is important to perform close sequence properly.
 */
public fun RoutingBuilder.webSocketRaw(protocol: String? = null, handler: suspend WebSocketServerSession.() -> Unit) {
    webSocketRaw(protocol, negotiateExtensions = false, handler)
}

/**
 * Bind RAW WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession]'s incoming channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSocket
 * it is important to perform close sequence properly.
 *
 * @param negotiateExtensions indicates if the server should negotiate installed WebSocket extensions.
 */
public fun RoutingBuilder.webSocketRaw(
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    plugin(WebSockets) // early require

    header(HttpHeaders.Connection, "Upgrade") {
        header(HttpHeaders.Upgrade, "websocket") {
            webSocketProtocol(protocol) {
                handle {
                    call.respondWebSocketRaw(protocol, negotiateExtensions) {
                        toServerSession(call).handler()
                    }
                }
            }
        }
    }
}

/**
 * Bind RAW WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw websocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession]'s incoming channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a websocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSocket
 * it is important to perform close sequence properly.
 */
@Deprecated(
    "Use webSocketRaw(protocol = protocol, handler = handler) instead.",
    ReplaceWith("webSocketRaw(protocol = webSocketProtocol, handler = webSocketHandler)"),
    DeprecationLevel.ERROR
)
@Suppress("UNUSED_PARAMETER")
public fun RoutingBuilder.webSocketRaw(
    webSocketProtocol: String,
    webSocketHandler: suspend WebSocketServerSession.() -> Unit,
    nothing: Nothing? = null
) {
    webSocketRaw(protocol = webSocketProtocol, handler = webSocketHandler)
}

/**
 * Bind WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * [DefaultWebSocketSession.incoming] will never contain any control frames and no fragmented frames could be found.
 * Default WebSocket implementation is handling ping/pongs, timeouts, close frames and reassembling fragmented frames.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the websocket termination sequence will be scheduled, so you shouldn't use
 * [DefaultWebSocketSession] anymore. However, WebSocket could live for a while until close sequence completed or
 * a timeout exceeds.
 */
public fun RoutingBuilder.webSocket(
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    webSocketRaw(protocol, negotiateExtensions = true) {
        proceedWebSocket(handler)
    }
}

/**
 * Bind WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * [DefaultWebSocketSession.incoming] will never contain any control frames and no fragmented frames could be found.
 * Default WebSocket implementation is handling ping/pongs, timeouts, close frames and reassembling fragmented frames.
 *
 * When a websocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the websocket termination sequence will be scheduled so you shouldn't use
 * [DefaultWebSocketSession] anymore. However websocket could live for a while until close sequence completed or
 * a timeout exceeds.
 */
@Deprecated(
    "Use webSocket(protocol = protocol, handler = handler) instead.",
    ReplaceWith("webSocket(protocol = webSocketProtocol, handler = webSocketHandler)"),
    DeprecationLevel.ERROR
)
public fun RoutingBuilder.webSocket(
    webSocketProtocol: String,
    webSocketHandler: suspend DefaultWebSocketServerSession.() -> Unit,
    @Suppress("UNUSED_PARAMETER")
    nothing: Nothing? = null
) {
    webSocket(protocol = webSocketProtocol, handler = webSocketHandler)
}

/**
 * Bind WebSocket at the current route + [path] optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * [DefaultWebSocketSession.incoming] will never contain any control frames and no fragmented frames could be found.
 * Default WebSocket implementation is handling ping/pongs, timeouts, close frames and reassembling fragmented frames.
 *
 * When a websocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket termination sequence will be scheduled so you shouldn't use
 * [DefaultWebSocketSession] anymore. However, WebSocket could live for a while until close sequence completed or
 * a timeout exceeds.
 */
public fun RoutingBuilder.webSocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    webSocketRaw(path, protocol, negotiateExtensions = true) {
        proceedWebSocket(handler)
    }
}

// these two functions could be potentially useful for users however it is not clear how to provide them better
// so for now they are still private

private suspend fun Call.respondWebSocketRaw(
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketSession.() -> Unit
) {
    respond(WebSocketUpgrade(this, protocol, negotiateExtensions, handler))
}

private fun RoutingBuilder.webSocketProtocol(protocol: String?, block: RoutingBuilder.() -> Unit) {
    if (protocol == null) {
        block()
    } else {
        createChild(WebSocketProtocolsSelector(protocol)).block()
    }
}

@OptIn(InternalAPI::class)
private suspend fun WebSocketServerSession.proceedWebSocket(handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    val webSockets = application.plugin(WebSockets)

    val session = DefaultWebSocketSession(
        this,
        webSockets.pingIntervalMillis,
        webSockets.timeoutMillis
    ).apply {
        val extensions = call.attributes[WebSockets.EXTENSIONS_KEY]
        start(extensions)
    }

    session.handleServerSession(call, handler)
    session.joinSession()
}

private suspend fun CoroutineScope.joinSession() {
    coroutineContext[Job]!!.join()
}

private suspend fun DefaultWebSocketSession.handleServerSession(
    call: Call,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    try {
        LOGGER.trace("Starting websocket session for ${call.request.uri}")
        val serverSession = toServerSession(call)
        handler(serverSession)
        close()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: ChannelIOException) {
        // don't log I/O exceptions
        throw io
    } catch (cause: Throwable) {
        call.application.log.error("Websocket handler failed", cause)
        throw cause
    }
}

private class WebSocketProtocolsSelector(
    val requiredProtocol: String
) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val protocols = context.call.request.headers[HttpHeaders.SecWebSocketProtocol]
        if (protocols == null) {
            LOGGER.trace("Skipping WebSocket plugin because no Sec-WebSocket-Protocol header provided.")
            return RouteSelectorEvaluation.FailedParameter
        }

        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation.Constant
        }

        LOGGER.trace(
            "Skipping WebSocket plugin because no Sec-WebSocket-Protocol " +
                "header $protocols is not matching $requiredProtocol."
        )
        return RouteSelectorEvaluation.FailedParameter
    }
}
