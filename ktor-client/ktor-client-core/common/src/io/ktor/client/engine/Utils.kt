/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Default user agent to use in ktor client.
 */
@InternalAPI
public val KTOR_DEFAULT_USER_AGENT: String = "Ktor client"

private val DATE_HEADERS = setOf(
    HttpHeaders.Date,
    HttpHeaders.Expires,
    HttpHeaders.LastModified,
    HttpHeaders.IfModifiedSince,
    HttpHeaders.IfUnmodifiedSince
)

/**
 * Merge headers from [content] and [requestHeaders] according to [OutgoingContent] properties
 */
@InternalAPI
public fun mergeHeaders(
    requestHeaders: Headers,
    content: OutgoingContent,
    block: (key: String, value: String) -> Unit
) {
    buildHeaders {
        appendAll(requestHeaders)
        appendAll(content.headers)
    }.forEach { key, values ->
        if (HttpHeaders.ContentLength == key) return@forEach // set later
        if (HttpHeaders.ContentType == key) return@forEach // set later

        // https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2
        if (DATE_HEADERS.contains(key)) {
            values.forEach { value ->
                block(key, value)
            }
        } else {
            val separator = if (HttpHeaders.Cookie == key) "; " else ","
            block(key, values.joinToString(separator))
        }
    }

    val missingAgent = requestHeaders[HttpHeaders.UserAgent] == null && content.headers[HttpHeaders.UserAgent] == null
    if (missingAgent && needUserAgent()) {
        block(HttpHeaders.UserAgent, KTOR_DEFAULT_USER_AGENT)
    }

    val type = content.contentType?.toString()
        ?: content.headers[HttpHeaders.ContentType]
        ?: requestHeaders[HttpHeaders.ContentType]

    val length = content.contentLength?.toString()
        ?: content.headers[HttpHeaders.ContentLength]
        ?: requestHeaders[HttpHeaders.ContentLength]

    type?.let { block(HttpHeaders.ContentType, it) }
    length?.let { block(HttpHeaders.ContentLength, it) }
}

/**
 * Returns current call context if exists, otherwise null.
 */
@InternalAPI
public suspend fun callContext(): CoroutineContext = coroutineContext[KtorCallContextElement]!!.callContext

/**
 * Coroutine context element containing call job.
 */
internal class KtorCallContextElement(val callContext: CoroutineContext) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = KtorCallContextElement

    companion object : CoroutineContext.Key<KtorCallContextElement>
}

/**
 * Attach [callJob] to user job using the following logic: when user job completes with exception, [callJob] completes
 * with exception too.
 */
@OptIn(InternalCoroutinesApi::class)
internal suspend inline fun attachToUserJob(callJob: Job) {
    val userJob = coroutineContext[Job] ?: return

    val cleanupHandler = userJob.invokeOnCompletion(onCancelling = true) { cause ->
        cause ?: return@invokeOnCompletion
        callJob.cancel(kotlinx.coroutines.CancellationException(cause.message))
    }

    callJob.invokeOnCompletion {
        cleanupHandler.dispose()
    }
}

private fun needUserAgent(): Boolean = !PlatformUtils.IS_BROWSER
