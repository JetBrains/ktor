/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Prepared statement for a HTTP client request.
 * This statement doesn't perform any network requests until [execute] method call.
 * [HttpStatement] is safe to execute multiple times.
 *
 * Example: [Streaming data](https://ktor.io/docs/response.html#streaming)
 */
public class HttpStatement(
    private val builder: HttpRequestBuilder,
    @PublishedApi
    internal val client: HttpClient
) {

    /**
     * Executes this statement and calls the [block] with the streaming [response].
     *
     * The [response] argument holds a network connection until the [block] isn't completed. You can read the body
     * on-demand or at once with [body<T>()] method.
     *
     * After [block] finishes, [response] will be completed body will be discarded or released depends on the engine configuration.
     *
     * Please note: the [response] instance will be canceled and shouldn't be passed outside of [block].
     */
    public suspend fun <T> execute(block: suspend (response: HttpResponse) -> T): T = unwrapRequestTimeoutException {
        val response = fetchStreamingResponse()

        try {
            return block(response)
        } finally {
            response.cleanup()
        }
    }

    /**
     * Executes this statement and download the response.
     * After the method execution finishes, the client downloads the response body in memory and release the connection.
     *
     * To receive exact type, consider using [body<T>()] method.
     */
    public suspend fun execute(): HttpResponse = fetchResponse()

    /**
     * Executes this statement and runs [HttpClient.responsePipeline] with the response and expected type [T].
     *
     * Note if T is a streaming type, you should manage how to close it manually.
     */
    @OptIn(InternalAPI::class)
    public suspend inline fun <reified T> body(): T = unwrapRequestTimeoutException {
        val response = fetchStreamingResponse()
        return try {
            response.body()
        } finally {
            response.complete()
        }
    }

    /**
     * Executes this statement and runs the [block] with a [HttpClient.responsePipeline] execution result.
     *
     * Note that T can be a streamed type such as [ByteReadChannel].
     */
    public suspend inline fun <reified T, R> body(
        crossinline block: suspend (response: T) -> R
    ): R = unwrapRequestTimeoutException {
        val response: HttpResponse = fetchStreamingResponse()
        try {
            val result = response.body<T>()
            return block(result)
        } finally {
            response.cleanup()
        }
    }

    /**
     * Returns [HttpResponse] with open streaming body.
     */
    @PublishedApi
    @OptIn(InternalAPI::class)
    internal suspend fun fetchStreamingResponse(): HttpResponse = unwrapRequestTimeoutException {
        val builder = HttpRequestBuilder().takeFromWithExecutionContext(builder)
        builder.skipSavingBody()

        val call = client.execute(builder)
        return call.response
    }

    /**
     * Returns [HttpResponse] with saved body.
     */
    @PublishedApi
    @OptIn(InternalAPI::class)
    internal suspend fun fetchResponse(): HttpResponse = unwrapRequestTimeoutException {
        val builder = HttpRequestBuilder().takeFromWithExecutionContext(builder)
        val call = client.execute(builder)
        val result = call.save().response
        call.response.cleanup()

        return result
    }

    /**
     * Completes [HttpResponse] and releases resources.
     */
    @PublishedApi
    @OptIn(InternalAPI::class, InternalCoroutinesApi::class)
    internal suspend fun HttpResponse.cleanup() {
        val job = coroutineContext[Job]!! as CompletableJob

        job.apply {
            complete()
            try {
                rawContent.cancel()
            } catch (_: Throwable) {
            }
            join()
        }
    }

    override fun toString(): String = "HttpStatement[${builder.url}]"
}
