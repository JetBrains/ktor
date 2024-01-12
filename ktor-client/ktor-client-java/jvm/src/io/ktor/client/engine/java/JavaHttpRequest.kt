/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.http.HttpRequest
import java.time.*
import java.util.*
import kotlin.coroutines.*

internal val DISALLOWED_HEADERS = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
    addAll(
        setOf(
            HttpHeaders.Connection,
            HttpHeaders.ContentLength,
            HttpHeaders.Date,
            HttpHeaders.Expect,
            HttpHeaders.From,
            HttpHeaders.Host,
            HttpHeaders.Upgrade,
            HttpHeaders.Via,
            HttpHeaders.Warning
        )
    )
}

@OptIn(InternalAPI::class)
internal fun HttpRequestData.convertToHttpRequest(callContext: CoroutineContext): HttpRequest {
    val builder = HttpRequest.newBuilder(url.toURI())

    with(builder) {
        getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutAttributes ->
            timeoutAttributes.requestTimeoutMillis?.let {
                if (!isTimeoutInfinite(it)) timeout(Duration.ofMillis(it))
            }
        }

        mergeHeaders(headers, body) { key, value ->
            if (!DISALLOWED_HEADERS.contains(key)) {
                header(key, value)
            }
        }

        method(method.value, body.convertToHttpRequestBody(callContext))
    }

    return builder.build()
}

@Suppress("DEPRECATION")
@OptIn(DelicateCoroutinesApi::class)
internal fun OutgoingContent.convertToHttpRequestBody(
    callContext: CoroutineContext
): HttpRequest.BodyPublisher = when (this) {
    is OutgoingContent.ByteArrayContent -> HttpRequest.BodyPublishers.ofByteArray(bytes())
    is OutgoingContent.ReadChannelContent -> JavaHttpRequestBodyPublisher(
        coroutineContext = callContext,
        contentLength = contentLength ?: -1
    ) { readFrom() }
    is OutgoingContent.WriteChannelContent -> JavaHttpRequestBodyPublisher(
        coroutineContext = callContext,
        contentLength = contentLength ?: -1
    ) { GlobalScope.writer(callContext) { writeTo(channel) }.channel }
    is OutgoingContent.NoContent -> HttpRequest.BodyPublishers.noBody()
    else -> throw UnsupportedContentTypeException(this)
}
