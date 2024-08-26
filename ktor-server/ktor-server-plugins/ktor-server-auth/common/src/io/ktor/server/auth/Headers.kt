/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.parsing.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

/**
 * Parses an authorization header from a [ApplicationRequest] returning a [HttpAuthHeader].
 */
public fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthHeader? = headers.parseAuthorizationHeader()

internal fun Headers.parseAuthorizationHeader(): HttpAuthHeader? = get(HttpHeaders.Authorization)?.let {
    try {
        parseAuthorizationHeader(it)
    } catch (cause: ParseException) {
        throw BadRequestException("Invalid auth header", cause)
    }
}
