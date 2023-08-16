/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Adds a route to handle Server-Sent Events (SSE) at the specified [path] using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param path URL path at which to handle SSE requests.
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [ServerSSESession]
 * to send events to the connected clients.
 *
 * @see ServerSSESession
 */
public fun RoutingBuilder.sse(path: String, handler: suspend ServerSSESession.() -> Unit) {
    plugin(SSE)

    route(path, HttpMethod.Get) {
        sse(handler)
    }
}

/**
 * Adds a route to handle Server-Sent Events (SSE) using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [ServerSSESession]
 * to send events to the connected clients.
 *
 * @see ServerSSESession
 */
public fun RoutingBuilder.sse(handler: suspend ServerSSESession.() -> Unit) {
    plugin(SSE)

    handle {
        call.respond(SSEServerContent(call, handler))
    }
}
