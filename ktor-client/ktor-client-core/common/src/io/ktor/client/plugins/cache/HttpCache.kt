/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION")

package io.ktor.client.plugins.cache

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

internal object CacheControl {
    internal val NO_STORE = HeaderValue("no-store")
    internal val NO_CACHE = HeaderValue("no-cache")
    internal val PRIVATE = HeaderValue("private")
    internal val ONLY_IF_CACHED = HeaderValue("only-if-cached")
    internal val MUST_REVALIDATE = HeaderValue("must-revalidate")
}

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpCache")

/**
 * A plugin that allows you to save previously fetched resources in an in-memory cache.
 * For example, if you make two consequent requests to a resource with the configured `Cache-Control` header,
 * the client executes only the first request and skips the second one since data is already saved in a cache.
 *
 * You can learn more from [Caching](https://ktor.io/docs/client-caching.html).
 */
public class HttpCache private constructor(
    @Deprecated("This will become internal", level = DeprecationLevel.ERROR)
    @Suppress("DEPRECATION_ERROR")
    internal val publicStorage: HttpCacheStorage,
    @Deprecated("This will become internal", level = DeprecationLevel.ERROR)
    @Suppress("DEPRECATION_ERROR")
    internal val privateStorage: HttpCacheStorage,
    private val publicStorageNew: CacheStorage,
    private val privateStorageNew: CacheStorage,
    private val useOldStorage: Boolean,
    internal val isSharedClient: Boolean
) {
    /**
     * A configuration for the [HttpCache] plugin.
     */
    @KtorDsl
    public class Config {
        internal var publicStorageNew: CacheStorage = CacheStorage.Unlimited()
        internal var privateStorageNew: CacheStorage = CacheStorage.Unlimited()
        internal var useOldStorage = false

        /**
         * Specifies if the client where this plugin is installed is shared among multiple users.
         * When set to true, all responses with `private` Cache-Control directive will not be cached.
         */
        public var isShared: Boolean = false

        /**
         * Specifies a storage for public cache entries.
         *
         * [HttpCacheStorage.Unlimited] by default.
         */
        @Deprecated(
            "This will become internal. Use setter method instead with new storage interface",
            level = DeprecationLevel.ERROR
        )
        @Suppress("DEPRECATION_ERROR")
        public var publicStorage: HttpCacheStorage = HttpCacheStorage.Unlimited()
            set(value) {
                useOldStorage = true
                field = value
            }

        /**
         * Specifies a storage for private cache entries.
         *
         * [HttpCacheStorage.Unlimited] by default.
         *
         * Consider using [HttpCacheStorage.Disabled] if the client is used as intermediate.
         */
        @Deprecated(
            "This will become internal. Use setter method instead with new storage interface",
            level = DeprecationLevel.ERROR
        )
        @Suppress("DEPRECATION_ERROR")
        public var privateStorage: HttpCacheStorage = HttpCacheStorage.Unlimited()
            set(value) {
                useOldStorage = true
                field = value
            }

        /**
         * Specifies a storage for public cache entries.
         *
         * [CacheStorage.Unlimited] by default.
         */
        public fun publicStorage(storage: CacheStorage) {
            publicStorageNew = storage
        }

        /**
         * Specifies a storage for private cache entries.
         *
         * [CacheStorage.Unlimited] by default.
         *
         * Consider using [CacheStorage.Disabled] if the client is used as intermediate.
         */
        public fun privateStorage(storage: CacheStorage) {
            privateStorageNew = storage
        }
    }

    public companion object : HttpClientPlugin<Config, HttpCache> {
        override val key: AttributeKey<HttpCache> = AttributeKey("HttpCache")

        public val HttpResponseFromCache: EventDefinition<HttpResponse> = EventDefinition()

        override fun prepare(block: Config.() -> Unit): HttpCache {
            val config = Config().apply(block)

            with(config) {
                @Suppress("DEPRECATION_ERROR")
                return HttpCache(
                    publicStorage = publicStorage,
                    privateStorage = privateStorage,
                    publicStorageNew = publicStorageNew,
                    privateStorageNew = privateStorageNew,
                    useOldStorage = useOldStorage,
                    isSharedClient = isShared
                )
            }
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: HttpCache, scope: HttpClient) {
            val CachePhase = PipelinePhase("Cache")
            scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, CachePhase)

            scope.sendPipeline.intercept(CachePhase) { content ->
                if (content !is OutgoingContent.NoContent) return@intercept
                if (context.method != HttpMethod.Get || !context.url.protocol.canStore()) return@intercept

                if (plugin.useOldStorage) {
                    interceptSendLegacy(plugin, content, scope)
                    return@intercept
                }

                val cache = plugin.findResponse(context, content)
                if (cache == null) {
                    LOGGER.trace("No cached response for ${context.url} found")
                    val header = parseHeaderValue(context.headers[HttpHeaders.CacheControl])
                    if (CacheControl.ONLY_IF_CACHED in header) {
                        LOGGER.trace("No cache found and \"only-if-cached\" set for ${context.url}")
                        proceedWithMissingCache(scope)
                    }
                    return@intercept
                }
                val validateStatus = shouldValidate(cache.expires, cache.headers, context)

                if (validateStatus == ValidateStatus.ShouldNotValidate) {
                    val cachedCall = cache
                        .createResponse(scope, RequestForCache(context.build()), context.executionContext)
                        .call
                    proceedWithCache(scope, cachedCall)
                    return@intercept
                }

                if (validateStatus == ValidateStatus.ShouldWarn) {
                    proceedWithWarning(cache, scope, context.executionContext)
                    return@intercept
                }

                cache.headers[HttpHeaders.ETag]?.let { etag ->
                    LOGGER.trace("Adding If-None-Match=$etag for ${context.url}")
                    context.header(HttpHeaders.IfNoneMatch, etag)
                }
                cache.headers[HttpHeaders.LastModified]?.let {
                    LOGGER.trace("Adding If-Modified-Since=$it for ${context.url}")
                    context.header(HttpHeaders.IfModifiedSince, it)
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                if (response.call.request.method != HttpMethod.Get) return@intercept

                if (plugin.useOldStorage) {
                    interceptReceiveLegacy(response, plugin, scope)
                    return@intercept
                }

                if (response.status.isSuccess()) {
                    LOGGER.trace("Caching response for ${response.call.request.url}")
                    val cachedData = plugin.cacheResponse(response)
                    if (cachedData != null) {
                        val reusableResponse = cachedData
                            .createResponse(scope, response.request, response.coroutineContext)
                        proceedWith(reusableResponse)
                        return@intercept
                    }
                }

                if (response.status == HttpStatusCode.NotModified) {
                    LOGGER.trace("Not modified response for ${response.call.request.url}, replying from cache")
                    val responseFromCache = plugin.findAndRefresh(response.call.request, response)
                        ?: throw InvalidCacheStateException(response.call.request.url)

                    scope.monitor.raise(HttpResponseFromCache, responseFromCache)
                    proceedWith(responseFromCache)
                }
            }
        }

        internal suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithCache(
            scope: HttpClient,
            cachedCall: HttpClientCall
        ) {
            finish()
            scope.monitor.raise(HttpResponseFromCache, cachedCall.response)
            proceedWith(cachedCall)
        }

        @OptIn(InternalAPI::class)
        private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithWarning(
            cachedResponse: CachedResponseData,
            scope: HttpClient,
            callContext: CoroutineContext
        ) {
            val request = context.build()
            val response = HttpResponseData(
                statusCode = cachedResponse.statusCode,
                requestTime = cachedResponse.requestTime,
                headers = Headers.build {
                    appendAll(cachedResponse.headers)
                    append(HttpHeaders.Warning, "110")
                },
                version = cachedResponse.version,
                body = ByteReadChannel(cachedResponse.body),
                callContext = callContext
            )
            val call = HttpClientCall(scope, request, response)
            finish()
            scope.monitor.raise(HttpResponseFromCache, call.response)
            proceedWith(call)
        }

        @OptIn(InternalAPI::class)
        internal suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithMissingCache(
            scope: HttpClient
        ) {
            finish()
            val request = context.build()
            val response = HttpResponseData(
                statusCode = HttpStatusCode.GatewayTimeout,
                requestTime = GMTDate(),
                headers = Headers.Empty,
                version = HttpProtocolVersion.HTTP_1_1,
                body = ByteReadChannel(ByteArray(0)),
                callContext = request.executionContext
            )
            val call = HttpClientCall(scope, request, response)
            proceedWith(call)
        }
    }

    private suspend fun cacheResponse(response: HttpResponse): CachedResponseData? {
        val request = response.call.request
        val responseCacheControl: List<HeaderValue> = response.cacheControl()
        val requestCacheControl: List<HeaderValue> = request.cacheControl()

        val isPrivate = CacheControl.PRIVATE in responseCacheControl
        val storage = when {
            isPrivate && isSharedClient -> return null
            isPrivate -> privateStorageNew
            else -> publicStorageNew
        }

        if (CacheControl.NO_STORE in responseCacheControl || CacheControl.NO_STORE in requestCacheControl) {
            return null
        }

        return storage.store(response, response.varyKeys(), isSharedClient)
    }

    private suspend fun findAndRefresh(request: HttpRequest, response: HttpResponse): HttpResponse? {
        val url = response.call.request.url
        val cacheControl = response.cacheControl()

        val isPrivate = CacheControl.PRIVATE in cacheControl
        val storage = when {
            isPrivate && isSharedClient -> return null
            isPrivate -> privateStorageNew
            else -> publicStorageNew
        }

        val varyKeysFrom304 = response.varyKeys()
        val cache = findResponse(storage, varyKeysFrom304, url, request) ?: return null
        val newVaryKeys = varyKeysFrom304.ifEmpty { cache.varyKeys }
        storage.store(request.url, cache.copy(newVaryKeys, response.cacheExpires(isSharedClient)))
        return cache.createResponse(request.call.client, request, response.coroutineContext)
    }

    private suspend fun findResponse(
        storage: CacheStorage,
        varyKeys: Map<String, String>,
        url: Url,
        request: HttpRequest
    ): CachedResponseData? = when {
        varyKeys.isNotEmpty() -> {
            storage.find(url, varyKeys)
        }

        else -> {
            val requestHeaders = mergedHeadersLookup(request.content, request.headers::get, request.headers::getAll)
            storage.findAll(url)
                .sortedByDescending { it.responseTime }
                .firstOrNull { cachedResponse ->
                    cachedResponse.varyKeys.all { (key, value) -> requestHeaders(key) == value }
                }
        }
    }

    private suspend fun findResponse(context: HttpRequestBuilder, content: OutgoingContent): CachedResponseData? {
        val url = Url(context.url)
        val lookup = mergedHeadersLookup(content, context.headers::get, context.headers::getAll)

        val cachedResponses = privateStorageNew.findAll(url) + publicStorageNew.findAll(url)
        for (item in cachedResponses) {
            val varyKeys = item.varyKeys
            if (varyKeys.isEmpty() || varyKeys.all { (key, value) -> lookup(key) == value }) {
                return item
            }
        }

        return null
    }
}

@OptIn(InternalAPI::class)
internal fun mergedHeadersLookup(
    content: OutgoingContent,
    headerExtractor: (String) -> String?,
    allHeadersExtractor: (String) -> List<String>?,
): (String) -> String = block@{ header ->
    return@block when (header) {
        HttpHeaders.ContentLength -> content.contentLength?.toString() ?: ""
        HttpHeaders.ContentType -> content.contentType?.toString() ?: ""
        HttpHeaders.UserAgent -> {
            content.headers[HttpHeaders.UserAgent] ?: headerExtractor(HttpHeaders.UserAgent) ?: KTOR_DEFAULT_USER_AGENT
        }

        else -> {
            val value = content.headers.getAll(header) ?: allHeadersExtractor(header) ?: emptyList()
            value.joinToString(";")
        }
    }
}

public class InvalidCacheStateException(requestUrl: Url) : IllegalStateException(
    "The entry for url: $requestUrl was removed from cache"
)

private fun URLProtocol.canStore(): Boolean = name == "http" || name == "https"

private class RequestForCache(data: HttpRequestData) : HttpRequest {
    override val call: HttpClientCall
        get() = throw IllegalStateException("This request has no call")
    override val method: HttpMethod = data.method
    override val url: Url = data.url
    override val attributes: Attributes = data.attributes
    override val content: OutgoingContent = data.body
    override val headers: Headers = data.headers
}
