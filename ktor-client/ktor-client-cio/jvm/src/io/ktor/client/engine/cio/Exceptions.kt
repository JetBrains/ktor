/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*

@OptIn(InternalAPI::class)
internal actual fun Throwable.mapToKtor(request: HttpRequestData): Throwable = when (cause?.rootCause) {
    is java.net.SocketTimeoutException -> SocketTimeoutException(request, cause)
    else -> cause
} ?: this
