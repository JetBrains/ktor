/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal.legacy

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import platform.Foundation.*

@OptIn(InternalAPI::class, UnsafeNumber::class)
internal suspend fun HttpRequestData.toNSUrlRequest(): NSMutableURLRequest {
    val url = url.toNSUrl()
    val nativeRequest = NSMutableURLRequest.requestWithURL(url).apply {
        setupSocketTimeout(this@toNSUrlRequest)

        body.toNSData()?.let {
            setHTTPBody(it)
        }

        mergeHeaders(headers, body) { key, value ->
            setValue(value, key)
        }

        setCachePolicy(NSURLRequestReloadIgnoringCacheData)
        setHTTPMethod(method.value)
    }

    return nativeRequest
}
