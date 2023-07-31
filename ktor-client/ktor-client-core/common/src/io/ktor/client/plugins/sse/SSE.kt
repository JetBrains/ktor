/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.engine.*
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.util.logging.*

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.sse.SSE")

/**
 * Indicates if a client engine supports Server-sent events.
 */
public object SSECapability : HttpClientEngineCapability<Unit> {
    override fun toString(): String = "SSECapability"
}

/**
 * Client Server-sent events plugin that allows you to establish an SSE connection to a server
 * and receive Server-sent events from it.
 *
 * ```kotlin
 * val client = HttpClient {
 *     install(SSE)
 * }
 * client.sse {
 *     val event = incoming.receive()
 * }
 * ```
 */
public val SSE: ClientPlugin<SSEConfig> = createClientPlugin(
    name = "SSE",
    createConfiguration = ::SSEConfig
) {
    val reconnectionTime = pluginConfig.reconnectionTime

    transformRequestBody { request, _, _ ->
        LOGGER.trace("Sending SSE request ${request.url}")
        request.setCapability(SSECapability, Unit)

        SSEContent(reconnectionTime)
    }

    transformResponseBody { response, content, requestedType ->
        if (content !is ClientSSESession) {
            LOGGER.trace("Skipping non SSE response from ${response.request.url}: $content")
            content
        } else {
            LOGGER.trace("Receive SSE session from ${response.request.url}: $content")
            HttpResponseContainer(requestedType, content)
        }
    }
}
