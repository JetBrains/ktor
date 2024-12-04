/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.http.*

/**
 * Sets the [HttpRequestBuilder.url] from [url].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.request.url)
 */
public fun HttpRequestBuilder.url(url: java.net.URL): URLBuilder = this.url.takeFrom(url)

/**
 * Constructs a [HttpRequestBuilder] from [url].
 */
public operator fun HttpRequestBuilder.Companion.invoke(url: java.net.URL): HttpRequestBuilder =
    HttpRequestBuilder().apply { url(url) }
