/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.util

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

/**
 * Creates an url using current call's schema, path and parameters as initial
 */
public fun UrlBuilder.Companion.createFromCall(call: ApplicationCall): UrlBuilder {
    val origin = call.request.origin

    val builder = UrlBuilder()
    builder.protocol = UrlProtocol.byName[origin.scheme] ?: UrlProtocol(origin.scheme, 0)
    builder.host = origin.serverHost
    builder.port = origin.serverPort
    builder.encodedPath = call.request.path()
    builder.parameters.appendAll(call.request.queryParameters)

    return builder
}

/**
 * Construct a URL
 */
public fun url(block: UrlBuilder.() -> Unit): String = UrlBuilder().apply(block).buildString()

/**
 * Creates an url using current call's schema, path and parameters as initial
 * and then invokes [block] function on the url builder so amend parameters
 */
public inline fun ApplicationCall.url(block: UrlBuilder.() -> Unit = {}): String =
    UrlBuilder.createFromCall(this).apply(block).buildString()
