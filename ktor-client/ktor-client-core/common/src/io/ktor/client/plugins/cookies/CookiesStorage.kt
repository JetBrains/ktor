/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cookies

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*

/**
 * A storage for [Cookie].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.cookies.CookiesStorage)
 */
public interface CookiesStorage : Closeable {
    /**
     * Gets a map of [String] to [Cookie] for a specific host.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.cookies.CookiesStorage.get)
     */
    public suspend fun get(requestUrl: Url): List<Cookie>

    /**
     * Sets a [cookie] for the specified host.
     */
    public suspend fun addCookie(requestUrl: Url, cookie: Cookie)
}

/**
 * Adds a [cookie] with the [urlString] key to storage.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.cookies.addCookie)
 */
public suspend fun CookiesStorage.addCookie(urlString: String, cookie: Cookie) {
    addCookie(Url(urlString), cookie)
}

/**
 * Checks if [Cookie] matches [requestUrl].
 */
public fun Cookie.matches(requestUrl: Url): Boolean {
    val domain = domain?.toLowerCasePreservingASCIIRules()?.trimStart('.')
        ?: error("Domain field should have the default value")

    val path = with(path) {
        val current = path ?: error("Path field should have the default value")
        if (current.endsWith('/')) current else "$path/"
    }

    val host = requestUrl.host.toLowerCasePreservingASCIIRules()
    val requestPath = let {
        val pathInRequest = requestUrl.encodedPath
        if (pathInRequest.endsWith('/')) pathInRequest else "$pathInRequest/"
    }

    if (host != domain && (hostIsIp(host) || !host.endsWith(".$domain"))) {
        return false
    }

    if (path != "/" &&
        requestPath != path &&
        !requestPath.startsWith(path)
    ) {
        return false
    }

    return !(secure && !requestUrl.protocol.isSecure())
}

/**
 * Fills [Cookie] with default values from [requestUrl].
 */
public fun Cookie.fillDefaults(requestUrl: Url): Cookie {
    var result = this

    if (result.path?.startsWith("/") != true) {
        result = result.copy(path = requestUrl.encodedPath)
    }

    if (result.domain.isNullOrBlank()) {
        result = result.copy(domain = requestUrl.host)
    }

    return result
}
