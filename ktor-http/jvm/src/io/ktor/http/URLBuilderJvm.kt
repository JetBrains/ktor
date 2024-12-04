/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import java.net.*

/**
 * Construct [Url] from [String]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.invoke)
 */
public operator fun Url.Companion.invoke(fullUrl: String): Url = URLBuilder().apply {
    takeFrom(URI(fullUrl))
}.build()

/**
 * Hostname of current origin.
 *
 * It uses "localhost" for all platforms except js.
 */
public actual val URLBuilder.Companion.origin: String get() = "http://localhost"
