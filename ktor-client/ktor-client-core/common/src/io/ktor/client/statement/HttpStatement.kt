/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Represents a prepared HTTP request statement for [HttpClient].
 *
 * The [HttpStatement] class encapsulates a request configuration without executing it immediately.
 * This statement can be executed on-demand via various methods such as [execute], allowing for
 * deferred or multiple executions without creating a new request each time.
 *
 * ## Deferred Execution
 * `HttpStatement` does not initiate any network activity until an execution method is called.
 * It is safe to execute multiple times, which can be useful in scenarios requiring reusability of
 * the same request configuration.
 *
 * Example: [Streaming data](https://ktor.io/docs/response.html#streaming)
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.statement.HttpStatement)
 */
public class HttpStatement(
    private val builder: HttpRequestBuilder,
    @PublishedApi
    internal val client: HttpClient
) {

    /**
     * Executes the HTTP statement and invokes the provided [block] with the streaming [HttpResponse].
     *
     * The [response] holds an open network connection until [block] completes.
     * You can access the response body incrementally (streaming) or load it entirely with [body<T>()].
     *
     * After [block] finishes, the [response] is finalized based on the engine's configuration—either discarded
     * or released.
     * The [response] object should not be accessed outside of [block] as it will be canceled upon
     * block completion.
     *
     * @param block A suspend function that receives the [HttpResponse] for streaming.
     * @return The result of executing [block] with the streaming [response].
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.statement.HttpStatement.execute)
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
     * Executes the HTTP statement and returns the full [HttpResponse].
     *
     * Once the method completes, the response body is downloaded fully into memory, and the connection is released.
     * This is suitable for requests where the entire response body is needed at once.
     *
     * For retrieving a specific data type directly, consider using [body<T>()].
     *
     * @return [HttpResponse] The complete response with the body loaded into memory.
     */
    public suspend fun execute(): HttpResponse = fetchResponse()

    /**
     * Executes the HTTP statement and processes the response through [HttpClient.responsePipeline] to retrieve
     * an instance of the specified type [T].
     *
     * If [T] represents a streaming type (such as [ByteReadChannel]), it is the caller's responsibility to
     * properly manage the resource, ensuring it is closed when no longer needed.
     *
     * @return The response body transformed to the specified type [T].
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
     * Executes the HTTP statement and processes the response of type [T] through the provided [block].
     *
     * This function is particularly useful for handling streaming responses, allowing you to process data on-the-fly
     * while the network connection remains open.
     * The [block] receives the streamed [response] and can be used to perform operations on the data as it arrives.
     *
     * Once [block] completes, the resources associated with the response are automatically cleaned up, freeing
     * any network or memory resources held by the response.
     *
     * ## Usage Example
     * ```
     * client.request {
     *     url("https://ktor.io")
     * }.body<ByteReadChannel> { channel ->
     *     // Process streaming data here
     * }
     * // Resources are released automatically after block completes
     * ```
     *
     * @param block A suspend function that handles the streamed [response] of type [T].
     * @return The result of [block] applied to the streaming [response].
     *
     * @note For streaming types (such as [ByteReadChannel]), ensure processing completes within [block], as resources
     * will be cleaned up automatically once [block] finishes.
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
