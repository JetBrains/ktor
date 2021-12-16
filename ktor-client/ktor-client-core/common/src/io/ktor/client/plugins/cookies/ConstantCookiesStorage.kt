/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cookies

import io.ktor.http.*
import io.ktor.util.date.*

/**
 * [CookiesStorage] that ignores [addCookie] and [now] and always returns the list of specified [cookies] when constructed.
 */
public class ConstantCookiesStorage(vararg cookies: Cookie) : CookiesStorage {
    private val storage: List<Cookie> = cookies.map { it.fillDefaults(URLBuilder().build()) }.toList()

    override suspend fun get(requestUrl: Url, now: GMTDate): List<Cookie> = storage.filter { it.matches(requestUrl) }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {}

    override fun close() {}
}
