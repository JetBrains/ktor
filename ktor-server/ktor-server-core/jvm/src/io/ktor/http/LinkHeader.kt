/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.response.*


/**
 * Append `Link` header to HTTP response
 */
public fun ApplicationResponse.link(header: LinkHeader): Unit = headers.append(HttpHeaders.Link, header.toString())

/**
 * Append `Link` header to HTTP response with specified [uri] and [rel]
 */
public fun ApplicationResponse.link(uri: String, vararg rel: String): Unit = link(LinkHeader(uri, *rel))
