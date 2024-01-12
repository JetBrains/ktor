/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import platform.Foundation.*

@OptIn(InternalAPI::class, UnsafeNumber::class)
internal suspend fun HttpRequestData.toNSUrlRequest(): NSMutableURLRequest {
    val url = url.toNSUrl()
    val nativeRequest = NSMutableURLRequest.requestWithURL(url).apply {
        setupSocketTimeout(this@toNSUrlRequest)
        body.toDataOrStream()?.let {
            if (it is NSInputStream) {
                setHTTPBodyStream(it)
            } else if (it is NSData) {
                setHTTPBody(it)
            }
        }

        mergeHeaders(headers, body) { key, value ->
            setValue(value, key)
        }

        setCachePolicy(NSURLRequestReloadIgnoringCacheData)
        setHTTPMethod(method.value)
    }

    return nativeRequest
}
