/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package io.ktor.client.plugins.cache

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.cache.HttpCache.Companion.proceedWithCache
import io.ktor.client.plugins.cache.HttpCache.Companion.proceedWithMissingCache
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

internal suspend fun PipelineContext<Any, HttpRequestBuilder>.interceptSendLegacy(
    plugin: HttpCache,
    content: OutgoingContent,
    scope: HttpClient
) {
    val cache = plugin.findResponse(context, content)
    if (cache == null) {
        val header = parseHeaderValue(context.headers[HttpHeaders.CacheControl])
        if (CacheControl.ONLY_IF_CACHED in header) {
            proceedWithMissingCache(scope)
        }
        return
    }
    val cachedCall = cache.produceResponse().call
    val validateStatus = shouldValidate(cache.expires, cache.response.headers, context)

    if (validateStatus == ValidateStatus.ShouldNotValidate) {
        proceedWithCache(scope, cachedCall)
        return
    }

    if (validateStatus == ValidateStatus.ShouldWarn) {
        proceedWithWarning(cachedCall, scope)
        return
    }

    cache.responseHeaders[HttpHeaders.ETag]?.let { etag ->
        context.header(HttpHeaders.IfNoneMatch, etag)
    }
    cache.responseHeaders[HttpHeaders.LastModified]?.let {
        context.header(HttpHeaders.IfModifiedSince, it)
    }
}

@OptIn(InternalAPI::class)
internal suspend fun PipelineContext<HttpResponse, Unit>.interceptReceiveLegacy(
    response: HttpResponse,
    plugin: HttpCache,
    scope: HttpClient
) {
    if (response.status.isSuccess()) {
        val reusableResponse = plugin.cacheResponse(response)
        proceedWith(reusableResponse)
        return
    }

    if (response.status == HttpStatusCode.NotModified) {
        response.complete()
        val responseFromCache = plugin.findAndRefresh(response.call.request, response)
            ?: throw InvalidCacheStateException(response.call.request.url)

        scope.monitor.raise(HttpCache.HttpResponseFromCache, responseFromCache)
        proceedWith(responseFromCache)
    }
}

@OptIn(InternalAPI::class)
private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithWarning(
    cachedCall: HttpClientCall,
    scope: HttpClient
) {
    val request = context.build()
    val response = HttpResponseData(
        statusCode = cachedCall.response.status,
        requestTime = cachedCall.response.requestTime,
        headers = Headers.build {
            appendAll(cachedCall.response.headers)
            append(HttpHeaders.Warning, "110")
        },
        version = cachedCall.response.version,
        body = cachedCall.response.content,
        callContext = cachedCall.response.coroutineContext
    )
    val call = HttpClientCall(scope, request, response)
    finish()
    scope.monitor.raise(HttpCache.HttpResponseFromCache, call.response)
    proceedWith(call)
}

private suspend fun HttpCache.cacheResponse(response: HttpResponse): HttpResponse {
    val request = response.call.request
    val responseCacheControl: List<HeaderValue> = response.cacheControl()
    val requestCacheControl: List<HeaderValue> = request.cacheControl()

    val storage = if (CacheControl.PRIVATE in responseCacheControl) privateStorage else publicStorage

    if (CacheControl.NO_STORE in responseCacheControl || CacheControl.NO_STORE in requestCacheControl) {
        return response
    }

    val cacheEntry = storage.store(request.url, response, isSharedClient)
    return cacheEntry.produceResponse()
}

private fun HttpCache.findAndRefresh(request: HttpRequest, response: HttpResponse): HttpResponse? {
    val url = response.call.request.url
    val cacheControl = response.cacheControl()

    val storage = if (CacheControl.PRIVATE in cacheControl) privateStorage else publicStorage

    val varyKeysFrom304 = response.varyKeys()
    val cache = findResponse(storage, varyKeysFrom304, url, request) ?: return null
    val newVaryKeys = varyKeysFrom304.ifEmpty { cache.varyKeys }
    storage.store(url, HttpCacheEntry(response.cacheExpires(isSharedClient), newVaryKeys, cache.response, cache.body))
    return cache.produceResponse()
}

private fun HttpCache.findResponse(
    storage: HttpCacheStorage,
    varyKeys: Map<String, String>,
    url: Url,
    request: HttpRequest
): HttpCacheEntry? = when {
    varyKeys.isNotEmpty() -> {
        storage.find(url, varyKeys)
    }

    else -> {
        val requestHeaders = mergedHeadersLookup(request.content, request.headers::get, request.headers::getAll)
        storage.findByUrl(url)
            .sortedByDescending { it.response.responseTime }
            .firstOrNull { cachedResponse ->
                cachedResponse.varyKeys.all { (key, value) -> requestHeaders(key) == value }
            }
    }
}

private fun HttpCache.findResponse(context: HttpRequestBuilder, content: OutgoingContent): HttpCacheEntry? {
    val url = Url(context.url)
    val lookup = mergedHeadersLookup(content, context.headers::get, context.headers::getAll)

    val cachedResponses = privateStorage.findByUrl(url) + publicStorage.findByUrl(url)
    for (item in cachedResponses) {
        val varyKeys = item.varyKeys
        if (varyKeys.isEmpty() || varyKeys.all { (key, value) -> lookup(key) == value }) {
            return item
        }
    }

    return null
}
