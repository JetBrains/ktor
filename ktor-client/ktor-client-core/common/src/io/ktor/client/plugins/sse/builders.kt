/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.sse.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.time.*

internal val reconnectionTimeAttr = AttributeKey<Duration>("SSEReconnectionTime")
internal val showCommentEventsAttr = AttributeKey<Boolean>("SSEShowCommentEvents")
internal val showRetryEventsAttr = AttributeKey<Boolean>("SSEShowRetryEvents")

/**
 * Installs the [SSE] plugin using the [config] as configuration.
 */
public fun HttpClientConfig<*>.SSE(config: SSEConfig.() -> Unit) {
    install(SSE) {
        config()
    }
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession {
    plugin(SSE)

    val sessionDeferred = CompletableDeferred<ClientSSESession>()
    val statement = prepareRequest {
        block()
        addAttribute(reconnectionTimeAttr, reconnectionTime)
        addAttribute(showCommentEventsAttr, showCommentEvents)
        addAttribute(showRetryEventsAttr, showRetryEvents)
    }
    @Suppress("SuspendFunctionOnCoroutineScope")
    launch {
        try {
            statement.body<ClientSSESession, Unit> { session ->
                sessionDeferred.complete(session)
            }
        } catch (cause: CancellationException) {
            sessionDeferred.cancel(cause)
        } catch (cause: Throwable) {
            sessionDeferred.completeExceptionally(SSEException(cause))
        }
    }
    return sessionDeferred.await()
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents) {
    url(scheme, host, port, path)
    block()
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents) {
    url.takeFrom(urlString)
    block()
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
) {
    val session = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents, request)
    try {
        block(session)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        throw SSEException(cause)
    } finally {
        session.cancel()
    }
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEvents(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
) {
    serverSentEvents(
        {
            url(scheme, host, port, path)
            request()
        },
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEvents(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
) {
    serverSentEvents(
        {
            url.takeFrom(urlString)
            request()
        },
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession =
    serverSentEventsSession(scheme, host, port, path, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(urlString, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(request, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit =
    serverSentEvents(scheme, host, port, path, reconnectionTime, showCommentEvents, showRetryEvents, request, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(urlString, reconnectionTime, showCommentEvents, showRetryEvents, request, block)

private fun <T : Any> HttpRequestBuilder.addAttribute(attributeKey: AttributeKey<T>, value: T?) {
    if (value != null) {
        attributes.put(attributeKey, value)
    }
}
