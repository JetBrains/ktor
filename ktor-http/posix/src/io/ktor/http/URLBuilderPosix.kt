/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Hostname of current origin.
 *
 * It uses "localhost" for all platforms except js.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.origin)
 */
public actual val URLBuilder.Companion.origin: String get() = "http://localhost"
