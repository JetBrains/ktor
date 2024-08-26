/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cache

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.io.*
import kotlin.collections.*

@OptIn(InternalAPI::class)
internal suspend fun HttpCacheEntry(isShared: Boolean, response: HttpResponse): HttpCacheEntry {
    val body = response.rawContent.readRemaining().readByteArray()
    return HttpCacheEntry(response.cacheExpires(isShared), response.varyKeys(), response, body)
}

/**
 * Client single response cache with [expires] and [varyKeys].
 */
public class HttpCacheEntry internal constructor(
    public val expires: GMTDate,
    public val varyKeys: Map<String, String>,
    public val response: HttpResponse,
    public val body: ByteArray
) {
    internal val responseHeaders: Headers = Headers.build {
        appendAll(response.headers)
    }

    internal fun produceResponse(): HttpResponse {
        val currentClient = response.call.client
        val call = SavedHttpCall(currentClient, response.call.request, response, body)
        return call.response
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is HttpCacheEntry) return false
        if (other === this) return true
        return varyKeys == other.varyKeys
    }

    override fun hashCode(): Int {
        return varyKeys.hashCode()
    }
}

internal fun HttpResponse.varyKeys(): Map<String, String> {
    val validationKeys = vary() ?: return emptyMap()

    val result = mutableMapOf<String, String>()
    val requestHeaders = call.request.headers

    for (key in validationKeys) {
        result[key] = requestHeaders[key] ?: ""
    }

    return result
}

internal fun HttpResponse.cacheExpires(isShared: Boolean, fallback: () -> GMTDate = { GMTDate() }): GMTDate {
    val cacheControl = cacheControl()

    val maxAgeKey = if (isShared && cacheControl.any { it.value.startsWith("s-maxage") }) "s-maxage" else "max-age"

    val maxAge = cacheControl.firstOrNull { it.value.startsWith(maxAgeKey) }
        ?.value?.split("=")
        ?.get(1)?.toLongOrNull()

    if (maxAge != null) {
        return requestTime + maxAge * 1000L
    }

    val expires = headers[HttpHeaders.Expires]
    return expires?.let {
        // Handle "0" case faster
        if (it == "0" || it.isBlank()) return fallback()

        return try {
            it.fromHttpToGmtDate()
        } catch (e: Throwable) {
            fallback()
        }
    } ?: fallback()
}

internal fun shouldValidate(
    cacheExpires: GMTDate,
    responseHeaders: Headers,
    request: HttpRequestBuilder
): ValidateStatus {
    val requestHeaders = request.headers
    val responseCacheControl = parseHeaderValue(responseHeaders.getAll(HttpHeaders.CacheControl)?.joinToString(","))
    val requestCacheControl = parseHeaderValue(requestHeaders.getAll(HttpHeaders.CacheControl)?.joinToString(","))

    if (CacheControl.NO_CACHE in requestCacheControl) {
        LOGGER.trace("\"no-cache\" is set for ${request.url}, should validate cached response")
        return ValidateStatus.ShouldValidate
    }

    val requestMaxAge = requestCacheControl.firstOrNull { it.value.startsWith("max-age=") }
        ?.value?.split("=")
        ?.get(1)?.let { it.toIntOrNull() ?: 0 }
    if (requestMaxAge == 0) {
        LOGGER.trace("\"max-age\" is not set for ${request.url}, should validate cached response")
        return ValidateStatus.ShouldValidate
    }

    if (CacheControl.NO_CACHE in responseCacheControl) {
        LOGGER.trace("\"no-cache\" is set for ${request.url}, should validate cached response")
        return ValidateStatus.ShouldValidate
    }
    val validMillis = cacheExpires.timestamp - getTimeMillis()
    if (validMillis > 0) {
        LOGGER.trace("Cached response is valid for ${request.url}, should not validate")
        return ValidateStatus.ShouldNotValidate
    }
    if (CacheControl.MUST_REVALIDATE in responseCacheControl) {
        LOGGER.trace("\"must-revalidate\" is set for ${request.url}, should validate cached response")
        return ValidateStatus.ShouldValidate
    }

    val maxStale = requestCacheControl.firstOrNull { it.value.startsWith("max-stale=") }
        ?.value?.substring("max-stale=".length)
        ?.toIntOrNull() ?: 0
    val maxStaleMillis = maxStale * 1000L
    if (validMillis + maxStaleMillis > 0) {
        LOGGER.trace("Cached response is stale for ${request.url} but less than max-stale, should warn")
        return ValidateStatus.ShouldWarn
    }
    LOGGER.trace("Cached response is stale for ${request.url}, should validate cached response")
    return ValidateStatus.ShouldValidate
}

internal enum class ValidateStatus {
    ShouldValidate, ShouldNotValidate, ShouldWarn
}
