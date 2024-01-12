/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.math.*
import kotlin.random.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpRequestRetry")

/**
 * Occurs on request retry.
 */
public val HttpRequestRetryEvent: EventDefinition<HttpRetryEventData> = EventDefinition()

/**
 * Contains [HttpRequestRetry] configurations settings.
 */
@KtorDsl
public class HttpRequestRetryConfig {
    internal lateinit var shouldRetry: HttpRetryShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean
    internal lateinit var shouldRetryOnException: HttpRetryShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean
    internal lateinit var delayMillis: HttpRetryDelayContext.(Int) -> Long
    internal var modifyRequest: HttpRetryModifyRequestContext.(HttpRequestBuilder) -> Unit = {}
    internal var delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }

    /**
     * The maximum amount of retries to perform for a request.
     */
    public var maxRetries: Int = 0

    init {
        retryOnExceptionOrServerErrors(3)
        exponentialDelay()
    }

    /**
     * Disables retry.
     */
    public fun noRetry() {
        maxRetries = 0
        shouldRetry = { _, _ -> false }
        shouldRetryOnException = { _, _ -> false }
    }

    /**
     * Modifies a request before retrying.
     */
    public fun modifyRequest(block: HttpRetryModifyRequestContext.(HttpRequestBuilder) -> Unit) {
        modifyRequest = block
    }

    /**
     * Specifies retry logic for a response. The [block] accepts [HttpRequest] and [HttpResponse]
     * and should return `true` if this request should be retried.
     */
    public fun retryIf(
        maxRetries: Int = -1,
        block: HttpRetryShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean
    ) {
        if (maxRetries != -1) this.maxRetries = maxRetries
        shouldRetry = block
    }

    /**
     * Specifies retry logic for failed requests. The [block] accepts [HttpRequestBuilder]
     * and [Throwable] and should return true if this request should be retried.
     */
    public fun retryOnExceptionIf(
        maxRetries: Int = -1,
        block: HttpRetryShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean
    ) {
        if (maxRetries != -1) this.maxRetries = maxRetries
        shouldRetryOnException = block
    }

    /**
     * Enables retrying a request if an exception is thrown during the [HttpSend] phase
     * and specifies the number of retries.
     * By default, [HttpRequestTimeoutException], [ConnectTimeoutException] and [SocketTimeoutException]
     * are not retried.
     * Set [retryOnTimeout] to `true` to retry on timeout.
     * Note, that in this case, [HttpTimeout] plugin should be installed after [HttpRequestRetry].
     */
    public fun retryOnException(maxRetries: Int = -1, retryOnTimeout: Boolean = false) {
        retryOnExceptionIf(maxRetries) { _, cause ->
            when {
                cause.isTimeoutException() -> retryOnTimeout
                cause is CancellationException -> false
                else -> true
            }
        }
    }

    /**
     * Enables retrying a request if a 5xx response is received from a server
     * and specifies the number of retries.
     */
    public fun retryOnServerErrors(maxRetries: Int = -1) {
        retryIf(maxRetries) { _, response ->
            response.status.value.let { it in 500..599 }
        }
    }

    /**
     * Enables retrying a request if an exception is thrown during the [HttpSend] phase
     * or a 5xx response is received and specifies the number of retries.
     */
    public fun retryOnExceptionOrServerErrors(maxRetries: Int = -1) {
        retryOnServerErrors(maxRetries)
        retryOnException(maxRetries)
    }

    /**
     * Specifies delay logic for retries. The [block] accepts the number of retries
     * and should return the number of milliseconds to wait before retrying.
     */
    public fun delayMillis(
        respectRetryAfterHeader: Boolean = true,
        block: HttpRetryDelayContext.(retry: Int) -> Long
    ) {
        delayMillis = {
            if (respectRetryAfterHeader) {
                val retryAfter = response?.headers?.get(HttpHeaders.RetryAfter)?.toLongOrNull()?.times(1000)
                maxOf(block(it), retryAfter ?: 0)
            } else {
                block(it)
            }
        }
    }

    /**
     * Specifies a constant delay between retries.
     * This delay equals to `millis + [0..randomizationMs]` milliseconds.
     */
    public fun constantDelay(
        millis: Long = 1000,
        randomizationMs: Long = 1000,
        respectRetryAfterHeader: Boolean = true
    ) {
        check(millis > 0)
        check(randomizationMs >= 0)

        delayMillis(respectRetryAfterHeader) {
            millis + randomMs(randomizationMs)
        }
    }

    /**
     * Specifies an exponential delay between retries, which is calculated using the Exponential backoff algorithm.
     * This delay equals to `base ^ retryCount * 1000 + [0..randomizationMs]`
     */
    public fun exponentialDelay(
        base: Double = 2.0,
        maxDelayMs: Long = 60000,
        randomizationMs: Long = 1000,
        respectRetryAfterHeader: Boolean = true
    ) {
        check(base > 0)
        check(maxDelayMs > 0)
        check(randomizationMs >= 0)

        delayMillis(respectRetryAfterHeader) { retry ->
            val delay = minOf(base.pow(retry).toLong() * 1000L, maxDelayMs)
            delay + randomMs(randomizationMs)
        }
    }

    /**
     * A function that waits for the specified number of milliseconds. Uses [kotlinx.coroutines.delay] by default.
     * Useful for tests.
     */
    public fun delay(block: suspend (Long) -> Unit) {
        delay = block
    }

    private fun randomMs(randomizationMs: Long): Long =
        if (randomizationMs == 0L) 0L else Random.nextLong(randomizationMs)
}

/**
 * A plugin that enables the client to retry failed requests.
 * The default retry policy is 3 retries with exponential delay.
 * Typical usages:
 * ```
 * // use predefined retry policies
 * install(HttpRequestRetry) {
 *      retryOnServerErrors(maxRetries = 3)
 *      exponentialDelay()
 * }
 *
 * // use custom policies
 * install(HttpRequestRetry) {
 *      maxRetries = 5
 *      retryIf { request, response -> !response.status.isSuccess() }
 *      retryOnExceptionIf { request, cause -> cause is NetworkError }
 *      delayMillis { retry -> retry * 3000 } // will retry in 3, 6, 9, etc. seconds
 *      modifyRequest { it.headers.append("X_RETRY_COUNT", retryCount.toString()) }
 * }
 * ```
 */
@Suppress("NAME_SHADOWING")
public val HttpRequestRetry: ClientPlugin<HttpRequestRetryConfig> = createClientPlugin(
    "RetryFeature",
    ::HttpRequestRetryConfig
) {

    val shouldRetry: HttpRetryShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean =
        pluginConfig.shouldRetry
    val shouldRetryOnException: HttpRetryShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean =
        pluginConfig.shouldRetryOnException
    val delayMillis: HttpRetryDelayContext.(Int) -> Long = pluginConfig.delayMillis
    val delay: suspend (Long) -> Unit = pluginConfig.delay
    val maxRetries: Int = pluginConfig.maxRetries
    val modifyRequest: HttpRetryModifyRequestContext.(HttpRequestBuilder) -> Unit =
        pluginConfig.modifyRequest

    fun shouldRetry(
        retryCount: Int,
        maxRetries: Int,
        shouldRetry: HttpRetryShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean,
        call: HttpClientCall
    ) = retryCount < maxRetries && shouldRetry(
        HttpRetryShouldRetryContext(retryCount + 1),
        call.request,
        call.response
    )

    fun shouldRetryOnException(
        retryCount: Int,
        maxRetries: Int,
        shouldRetry: HttpRetryShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean,
        subRequest: HttpRequestBuilder,
        cause: Throwable
    ) = retryCount < maxRetries && shouldRetry(
        HttpRetryShouldRetryContext(retryCount + 1),
        subRequest,
        cause
    )

    fun prepareRequest(request: HttpRequestBuilder): HttpRequestBuilder {
        val subRequest = HttpRequestBuilder().takeFrom(request)
        request.executionContext.invokeOnCompletion { cause ->
            val subRequestJob = subRequest.executionContext as CompletableJob
            if (cause == null) {
                subRequestJob.complete()
            } else subRequestJob.completeExceptionally(cause)
        }
        return subRequest
    }

    on(Send) { request ->
        var retryCount = 0
        val shouldRetry = request.attributes.getOrNull(ShouldRetryPerRequestAttributeKey) ?: shouldRetry
        val shouldRetryOnException =
            request.attributes.getOrNull(ShouldRetryOnExceptionPerRequestAttributeKey) ?: shouldRetryOnException
        val maxRetries = request.attributes.getOrNull(MaxRetriesPerRequestAttributeKey) ?: maxRetries
        val delayMillis = request.attributes.getOrNull(RetryDelayPerRequestAttributeKey) ?: delayMillis
        val modifyRequest = request.attributes.getOrNull(ModifyRequestPerRequestAttributeKey) ?: modifyRequest

        var call: HttpClientCall
        var lastRetryData: HttpRetryEventData? = null
        while (true) {
            val subRequest = prepareRequest(request)

            val retryData = try {
                if (lastRetryData != null) {
                    val modifyRequestContext = HttpRetryModifyRequestContext(
                        request,
                        lastRetryData.response,
                        lastRetryData.cause,
                        lastRetryData.retryCount
                    )
                    modifyRequest(modifyRequestContext, subRequest)
                }
                call = proceed(subRequest)
                if (!shouldRetry(retryCount, maxRetries, shouldRetry, call)) {
                    break
                }
                HttpRetryEventData(subRequest, ++retryCount, call.response, null)
            } catch (cause: Throwable) {
                if (!shouldRetryOnException(retryCount, maxRetries, shouldRetryOnException, subRequest, cause)) {
                    throw cause
                }
                HttpRetryEventData(subRequest, ++retryCount, null, cause)
            }

            lastRetryData = retryData
            client.monitor.raise(HttpRequestRetryEvent, lastRetryData)

            val delayContext =
                HttpRetryDelayContext(lastRetryData.request, lastRetryData.response, lastRetryData.cause)
            delay(delayMillis(delayContext, retryCount))
            LOGGER.trace("Retrying request ${request.url} attempt: $retryCount")
        }
        call
    }
}

/**
 * A context for [HttpRequestRetry.Configuration.shouldRetry]
 * and [HttpRequestRetry.Configuration.shouldRetryOnException]
 */
public class HttpRetryShouldRetryContext(
    /**
     * A retry count starting from 1
     */
    public val retryCount: Int
)

/**
 * A context for [HttpRequestRetry.Configuration.delayMillis].
 * Contains a non-null [response] or [cause] but not both.
 */
public class HttpRetryDelayContext internal constructor(
    public val request: HttpRequestBuilder,
    public val response: HttpResponse?,
    public val cause: Throwable?
)

/**
 * A context for [HttpRequestRetry.Configuration.modifyRequest].
 * Contains a non-null [response] or [cause] but not both.
 */
public class HttpRetryModifyRequestContext internal constructor(
    /**
     * An original request
     */
    public val request: HttpRequestBuilder,
    public val response: HttpResponse?,
    public val cause: Throwable?,
    /**
     * A retry count that starts from 1
     */
    public val retryCount: Int,
)

/**
 * Data for the [HttpRequestRetryEvent] event. Contains a non-null [response] or [cause] but not both.
 */
public class HttpRetryEventData internal constructor(
    public val request: HttpRequestBuilder,
    public val retryCount: Int,
    public val response: HttpResponse?,
    public val cause: Throwable?
)

/**
 * Configures the [HttpRequestRetry] plugin on a per-request level.
 */
public fun HttpRequestBuilder.retry(block: HttpRequestRetryConfig.() -> Unit) {
    val configuration = HttpRequestRetryConfig().apply(block)
    attributes.put(ShouldRetryPerRequestAttributeKey, configuration.shouldRetry)
    attributes.put(ShouldRetryOnExceptionPerRequestAttributeKey, configuration.shouldRetryOnException)
    attributes.put(RetryDelayPerRequestAttributeKey, configuration.delayMillis)
    attributes.put(MaxRetriesPerRequestAttributeKey, configuration.maxRetries)
    attributes.put(ModifyRequestPerRequestAttributeKey, configuration.modifyRequest)
}

private val MaxRetriesPerRequestAttributeKey =
    AttributeKey<Int>("MaxRetriesPerRequestAttributeKey")

private val ShouldRetryPerRequestAttributeKey =
    AttributeKey<HttpRetryShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean>(
        "ShouldRetryPerRequestAttributeKey"
    )

private val ShouldRetryOnExceptionPerRequestAttributeKey =
    AttributeKey<HttpRetryShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean>(
        "ShouldRetryOnExceptionPerRequestAttributeKey"
    )

private val ModifyRequestPerRequestAttributeKey =
    AttributeKey<HttpRetryModifyRequestContext.(HttpRequestBuilder) -> Unit>(
        "ModifyRequestPerRequestAttributeKey"
    )

private val RetryDelayPerRequestAttributeKey =
    AttributeKey<HttpRetryDelayContext.(Int) -> Long>(
        "RetryDelayPerRequestAttributeKey"
    )

private fun Throwable.isTimeoutException(): Boolean {
    val exception = unwrapCancellationException()
    return exception is HttpRequestTimeoutException ||
        exception is ConnectTimeoutException ||
        exception is SocketTimeoutException
}
