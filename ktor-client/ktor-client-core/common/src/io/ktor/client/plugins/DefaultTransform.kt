/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.defaultTransformers")

/**
 * Install default transformers.
 * Usually installed by default so there is no need to use it
 * unless you have disabled it via [HttpClientConfig.useDefaultTransformers].
 */
@OptIn(InternalAPI::class)
public fun HttpClient.defaultTransformers() {
    requestPipeline.intercept(HttpRequestPipeline.Render) { body ->
        if (context.headers[HttpHeaders.Accept] == null) {
            context.headers.append(HttpHeaders.Accept, "*/*")
        }

        val contentType = context.contentType()
        val content = when (body) {
            is String -> {
                TextContent(body, contentType ?: ContentType.Text.Plain)
            }

            is ByteArray -> object : OutgoingContent.ByteArrayContent() {
                override val contentType: ContentType = contentType ?: ContentType.Application.OctetStream
                override val contentLength: Long = body.size.toLong()
                override fun bytes(): ByteArray = body
            }

            is ByteReadChannel -> object : OutgoingContent.ReadChannelContent() {
                override val contentLength = context.headers[HttpHeaders.ContentLength]?.toLong()
                override val contentType: ContentType = contentType ?: ContentType.Application.OctetStream
                override fun readFrom(): ByteReadChannel = body
            }

            is OutgoingContent -> body
            else -> platformRequestDefaultTransform(contentType, context, body)
        }
        if (content?.contentType != null) {
            context.headers.remove(HttpHeaders.ContentType)
            LOGGER.trace("Transformed with default transformers request body for ${context.url} from ${body::class}")
            proceedWith(content)
        }
    }

    responsePipeline.intercept(HttpResponsePipeline.Parse) { (info, body) ->
        if (body !is ByteReadChannel) return@intercept
        val response = context.response

        val result = when (info.type) {
            Unit::class -> {
                body.cancel()
                proceedWith(HttpResponseContainer(info, Unit))
            }

            Int::class -> {
                proceedWith(HttpResponseContainer(info, body.readRemaining().readText().toInt()))
            }

            Source::class,
            Input::class -> {
                proceedWith(HttpResponseContainer(info, body.readRemaining()))
            }

            ByteArray::class -> {
                val bytes = body.toByteArray()
                proceedWith(HttpResponseContainer(info, bytes))
            }

            ByteReadChannel::class -> {
                // the response job could be already completed, so the job holder
                // could be canceled immediately, but it doesn't matter
                // since the copying job is running under the client job
                val responseJobHolder = Job(response.coroutineContext[Job])
                val channel: ByteReadChannel = writer(this@defaultTransformers.coroutineContext) {
                    try {
                        body.copyTo(channel, limit = Long.MAX_VALUE)
                    } catch (cause: CancellationException) {
                        response.cancel(cause)
                        throw cause
                    } catch (cause: Throwable) {
                        response.cancel("Receive failed", cause)
                        throw cause
                    }
                }.also { writerJob ->
                    writerJob.invokeOnCompletion {
                        responseJobHolder.complete()
                    }
                }.channel

                proceedWith(HttpResponseContainer(info, channel))
            }

            HttpStatusCode::class -> {
                body.cancel()
                proceedWith(HttpResponseContainer(info, response.status))
            }

            else -> null
        }
        if (result != null) {
            LOGGER.trace(
                "Transformed with default transformers response body " +
                    "for ${context.request.url} to ${info.type}"
            )
        }
    }

    platformResponseDefaultTransformers()
}

internal expect fun platformRequestDefaultTransform(
    contentType: ContentType?,
    context: HttpRequestBuilder,
    body: Any
): OutgoingContent?

internal expect fun HttpClient.platformResponseDefaultTransformers()
