/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cookies

import io.ktor.http.*

/**
 * [CookiesStorage] that ignores [addCookie] and returns a list of specified [cookies] when constructed.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.cookies.ConstantCookiesStorage)
 */
public class ConstantCookiesStorage(vararg cookies: Cookie) : CookiesStorage {
    private val storage: List<Cookie> = cookies.map { it.fillDefaults(URLBuilder().build()) }.toList()

    override suspend fun get(requestUrl: Url): List<Cookie> = storage.filter { it.matches(requestUrl) }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {}

    override fun close() {}
}
