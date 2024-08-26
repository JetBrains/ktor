/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.sse.SSE")

/**
 * Indicates if a client engine supports Server-sent events.
 */
public data object SSECapability : HttpClientEngineCapability<Unit>

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
@OptIn(InternalAPI::class)
public val SSE: ClientPlugin<SSEConfig> = createClientPlugin(
    name = "SSE",
    createConfiguration = ::SSEConfig
) {
    val reconnectionTime = pluginConfig.reconnectionTime
    val showCommentEvents = pluginConfig.showCommentEvents
    val showRetryEvents = pluginConfig.showRetryEvents

    on(AfterRender) { request, content ->
        if (getAttributeValue(request, sseRequestAttr) != true) {
            return@on content
        }
        LOGGER.trace("Sending SSE request ${request.url}")
        request.setCapability(SSECapability, Unit)

        val localReconnectionTime = getAttributeValue(request, reconnectionTimeAttr)
        val localShowCommentEvents = getAttributeValue(request, showCommentEventsAttr)
        val localShowRetryEvents = getAttributeValue(request, showRetryEventsAttr)

        request.attributes.put(ResponseAdapterAttributeKey, SSEClientResponseAdapter())
        content.contentType?.let { request.contentType(it) }
        SSEClientContent(
            localReconnectionTime ?: reconnectionTime,
            localShowCommentEvents ?: showCommentEvents,
            localShowRetryEvents ?: showRetryEvents,
            content
        )
    }

    client.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, session) ->
        val response = context.response
        val status = response.status
        val contentType = response.contentType()
        val requestContent = response.request.content

        if (requestContent !is SSEClientContent) {
            LOGGER.trace("Skipping non SSE response from ${response.request.url}")
            return@intercept
        }
        if (status != HttpStatusCode.OK) {
            throw SSEClientException(
                response,
                message = "Expected status code ${HttpStatusCode.OK.value} but was ${status.value}"
            )
        }
        if (contentType?.withoutParameters() != ContentType.Text.EventStream) {
            throw SSEClientException(
                response,
                message = "Expected Content-Type ${ContentType.Text.EventStream} but was $contentType"
            )
        }
        if (session !is SSESession) {
            throw SSEClientException(
                response,
                message = "Expected ${SSESession::class.simpleName} content but was $session"
            )
        }

        LOGGER.trace("Receive SSE session from ${response.request.url}: $session")
        proceedWith(HttpResponseContainer(info, ClientSSESession(context, session)))
    }
}

/**
 * Represents an exception which can be thrown during client SSE session.
 */
public class SSEClientException(
    public val response: HttpResponse? = null,
    public override val cause: Throwable? = null,
    public override val message: String? = null
) : IllegalStateException()

private fun <T : Any> getAttributeValue(request: HttpRequestBuilder, attributeKey: AttributeKey<T>): T? {
    return request.attributes.getOrNull(attributeKey)
}

private object AfterRender : ClientHook<suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent> {
    override fun install(
        client: HttpClient,
        handler: suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent
    ) {
        val phase = PipelinePhase("AfterRender")
        client.requestPipeline.insertPhaseAfter(HttpRequestPipeline.Render, phase)
        client.requestPipeline.intercept(phase) { content ->
            if (content !is OutgoingContent) return@intercept
            proceedWith(handler(context, content))
        }
    }
}
