/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.response

import io.ktor.http.*
import io.ktor.util.date.*

/**
 * Server's response cookies.
 * @see [ApplicationResponse.cookies]
 */
public class ResponseCookies(private val response: PipelineResponse) {
    /**
     * Gets a cookie from a response's `Set-Cookie` header.
     */
    public operator fun get(name: String): Cookie? = response.headers
        .values("Set-Cookie")
        .map { parseServerSetCookieHeader(it) }
        .firstOrNull { it.name == name }

    /**
     * Appends a cookie [item] using the `Set-Cookie` response header.
     */
    public fun append(item: Cookie) {
        response.headers.append("Set-Cookie", renderSetCookieHeader(item))
    }

    /**
     * Appends a cookie using the `Set-Cookie` response header from the specified parameters.
     */
    public fun append(
        name: String,
        value: String,
        encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
        maxAge: Long? = null,
        expires: GMTDate? = null,
        domain: String? = null,
        path: String? = null,
        secure: Boolean = false,
        httpOnly: Boolean = false,
        extensions: Map<String, String?> = emptyMap()
    ) {
        append(
            Cookie(
                name,
                value,
                encoding,
                maxAge?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                expires,
                domain,
                path,
                secure,
                httpOnly,
                extensions
            )
        )
    }

    /**
     * Appends an already expired cookie. Useful to remove client cookies.
     */
    @Deprecated(
        "This method doesn't bypass all flags and extensions so it will be removed in future " +
            "major release. Please consider using append with expires parameter instead.",
        replaceWith = ReplaceWith(
            "append(name, \"\", CookieEncoding.URI_ENCODING, 0, GMTDate(), domain, path, secure, httpOnly, extensions)"
        ),
        level = DeprecationLevel.ERROR
    )
    public fun appendExpired(name: String, domain: String? = null, path: String? = null) {
        append(name, "", domain = domain, path = path, expires = GMTDate.START)
    }
}
