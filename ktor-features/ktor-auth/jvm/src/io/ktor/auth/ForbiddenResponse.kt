/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.http.auth.*

/**
 * Response content with `403 Forbidden` status code and `WWW-Authenticate` header of supplied [challenges]
 * @param challenges to be passed with `WWW-Authenticate` header
 */
class ForbiddenResponse(vararg val challenges: HttpAuthHeader) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode?
        get() = HttpStatusCode.Forbidden

    override val headers: Headers
        get() = if (challenges.isNotEmpty())
            headersOf(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render() })
        else
            Headers.Empty
}

