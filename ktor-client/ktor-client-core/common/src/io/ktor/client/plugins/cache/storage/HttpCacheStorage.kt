/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION")

package io.ktor.client.plugins.cache.storage

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Cache storage interface.
 */
@Deprecated("Use new [CacheStorage] instead.", level = DeprecationLevel.ERROR)
@Suppress("DEPRECATION_ERROR")
public abstract class HttpCacheStorage {

    /**
     * Store [value] in cache storage for [url] key.
     */
    public abstract fun store(url: Url, value: HttpCacheEntry)

    /**
     * Find valid entry in cache storage with additional [varyKeys].
     */
    public abstract fun find(url: Url, varyKeys: Map<String, String>): HttpCacheEntry?

    /**
     * Find all matched [HttpCacheEntry] for [url].
     */
    public abstract fun findByUrl(url: Url): Set<HttpCacheEntry>

    public companion object {
        /**
         * Default unlimited cache storage.
         */
        public val Unlimited: () -> HttpCacheStorage = { UnlimitedCacheStorage() }

        /**
         * Disabled cache always empty and store nothing.
         */
        public val Disabled: HttpCacheStorage = DisabledCacheStorage
    }
}

@Suppress("DEPRECATION_ERROR")
internal suspend fun HttpCacheStorage.store(url: Url, value: HttpResponse, isShared: Boolean): HttpCacheEntry {
    val result = HttpCacheEntry(isShared, value)
    store(url, result)
    return result
}

/**
 * Cache storage interface.
 */
public interface CacheStorage {

    /**
     * Store [value] in cache storage for [url] key.
     */
    public suspend fun store(url: Url, data: CachedResponseData)

    /**
     * Find valid entry in cache storage with additional [varyKeys].
     */
    public suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData?

    /**
     * Find all matched [HttpCacheEntry] for [url].
     */
    public suspend fun findAll(url: Url): Set<CachedResponseData>

    public companion object {
        /**
         * Default unlimited cache storage.
         */
        public val Unlimited: () -> CacheStorage = { UnlimitedStorage() }

        /**
         * Disabled cache always empty and store nothing.
         */
        public val Disabled: CacheStorage = DisabledStorage
    }
}

/**
 * Store [response] in cache storage.
 */
@Deprecated(
    message = "Please use method with `response.varyKeys()` and `isShared` arguments",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("store(response, response.varyKeys(), isShared)")
)
public suspend fun CacheStorage.store(response: HttpResponse): CachedResponseData {
    return store(response, response.varyKeys())
}

/**
 * Store [response] with [varyKeys] in cache storage.
 */
@OptIn(InternalAPI::class)
public suspend fun CacheStorage.store(
    response: HttpResponse,
    varyKeys: Map<String, String>,
    isShared: Boolean = false
): CachedResponseData {
    val url = response.call.request.url
    val body = response.rawContent.readRemaining().readBytes()
    val data = CachedResponseData(
        url = response.call.request.url,
        statusCode = response.status,
        requestTime = response.requestTime,
        headers = response.headers,
        version = response.version,
        body = body,
        responseTime = response.responseTime,
        expires = response.cacheExpires(isShared),
        varyKeys = varyKeys
    )
    store(url, data)
    return data
}

internal fun CachedResponseData.createResponse(
    client: HttpClient,
    request: HttpRequest,
    responseContext: CoroutineContext
): HttpResponse {
    val response = object : HttpResponse() {
        override val call: HttpClientCall get() = throw IllegalStateException("This is a fake response")
        override val status: HttpStatusCode = statusCode
        override val version: HttpProtocolVersion = this@createResponse.version
        override val requestTime: GMTDate = this@createResponse.requestTime
        override val responseTime: GMTDate = this@createResponse.responseTime

        @InternalAPI
        override val rawContent: ByteReadChannel get() = throw IllegalStateException("This is a fake response")
        override val headers: Headers = this@createResponse.headers
        override val coroutineContext: CoroutineContext = responseContext
    }
    return SavedHttpCall(client, request, response, body).response
}

/**
 * Cached representation of response
 */
public class CachedResponseData(
    public val url: Url,
    public val statusCode: HttpStatusCode,
    public val requestTime: GMTDate,
    public val responseTime: GMTDate,
    public val version: HttpProtocolVersion,
    public val expires: GMTDate,
    public val headers: Headers,
    public val varyKeys: Map<String, String>,
    public val body: ByteArray
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedResponseData) return false

        if (url != other.url) return false
        if (varyKeys != other.varyKeys) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + varyKeys.hashCode()
        return result
    }

    internal fun copy(varyKeys: Map<String, String>, expires: GMTDate): CachedResponseData = CachedResponseData(
        url = url,
        statusCode = statusCode,
        requestTime = requestTime,
        responseTime = responseTime,
        version = version,
        expires = expires,
        headers = headers,
        varyKeys = varyKeys,
        body = body
    )
}
