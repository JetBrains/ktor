/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.*

/**
 * Executes a [HttpClient] request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.request.request)
 */
public suspend fun HttpClient.request(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend fun HttpClient.get(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.post(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.put(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.patch(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.options(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.head(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.delete(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareRequest(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend fun HttpClient.prepareGet(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareGet {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePost(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePost {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePut(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePut {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePatch(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePatch {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareOptions(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareOptions {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareHead(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareHead {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareDelete(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareDelete {
    this.url.takeFrom(url)
    block()
}
