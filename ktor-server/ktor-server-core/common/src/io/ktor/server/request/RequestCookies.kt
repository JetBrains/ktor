/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.util.collections.*

/**
 * Server request's cookies.
 * @see [ApplicationRequest.cookies]
 * @property request application request to fetch cookies from
 */
public open class RequestCookies(protected val request: ApplicationRequest) {
    private val map = ConcurrentMap<Pair<CookieEncoding, String>, String>()

    /**
     * Provides access to raw cookie values.
     * These values are not decoded so could have percent encoded values, quotes, escape characters, and so on.
     * It is recommended to use [get] instead.
     */
    public val rawCookies: Map<String, String> by lazy { fetchCookies() }

    /**
     * Gets a [name] cookie value decoded using an [encoding] strategy.
     */
    public operator fun get(name: String, encoding: CookieEncoding = CookieEncoding.URI_ENCODING): String? {
        val rawValue = rawCookies[name] ?: return null
        return map.computeIfAbsent(encoding to name) { decodeCookieValue(rawValue, encoding) }
    }

    /**
     * Fetches cookies from a [request]. Could access cookies using an engine's native API.
     */
    protected open fun fetchCookies(): Map<String, String> {
        val cookieHeaders = request.headers.getAll("Cookie") ?: return emptyMap()
        val map = HashMap<String, String>(cookieHeaders.size)
        for (cookieHeader in cookieHeaders) {
            val cookies = parseClientCookiesHeader(cookieHeader)
            map.putAll(cookies)
        }
        return map
    }
}
