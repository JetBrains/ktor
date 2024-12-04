/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import java.net.*

/**
 * Take URI components from [uri]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.takeFrom)
 */
public fun URLBuilder.takeFrom(uri: URI): URLBuilder {
    uri.scheme?.let {
        protocol = URLProtocol.createOrDefault(it)
        port = protocol.defaultPort
    }

    if (uri.port > 0) {
        port = uri.port
    } else {
        when (uri.scheme) {
            "http" -> port = 80
            "https" -> port = 443
        }
    }

    if (uri.rawUserInfo != null && uri.rawUserInfo.isNotEmpty()) {
        val parts = uri.rawUserInfo.split(":")
        encodedUser = parts.first()
        encodedPassword = parts.getOrNull(1)
    }

    uri.host?.let { host = it }
    encodedPath = uri.rawPath
    uri.rawQuery?.let {
        encodedParameters = ParametersBuilder().apply { appendAll(parseQueryString(it, decode = false)) }
    }
    if (uri.query?.isEmpty() == true) {
        trailingQuery = true
    }

    uri.rawFragment?.let { encodedFragment = it }
    return this
}

/**
 * Take URL components from [url]
 */
public fun URLBuilder.takeFrom(url: URL): URLBuilder = when {
    url.host.contains('_') -> takeFrom(url.toString())
    else -> takeFrom(url.toURI())
}

/**
 * Convert [Url] to [URI]
 */
public fun Url.toURI(): URI = URI(toString())

/**
 * Helper method that concisely creates a [Url] from a [URI]
 *
 * Creates [Url] from [URI]
 */
@Suppress("FunctionName")
public fun Url(uri: URI): Url = URLBuilder().takeFrom(uri).build()
