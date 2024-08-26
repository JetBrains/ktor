/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.content.*

/**
 * Response content with the `403 Forbidden` status code and the `WWW-Authenticate` header of supplied [challenges]
 * @param challenges to be passed in the `WWW-Authenticate` header.
 */
public class ForbiddenResponse(public vararg val challenges: HttpAuthHeader) : OutgoingContent.NoContent() {
    public constructor(challenge: HttpAuthHeader?) : this(*if (challenge == null) emptyArray() else arrayOf(challenge))

    override val status: HttpStatusCode
        get() = HttpStatusCode.Forbidden

    override val headers: Headers
        get() = if (challenges.isNotEmpty()) {
            headersOf(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render() })
        } else {
            Headers.Empty
        }
}
