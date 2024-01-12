/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal.legacy

import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.cinterop.*
import platform.Foundation.*

/**
 * Update [NSMutableURLRequest] and setup timeout interval that equal to socket interval specified by [HttpTimeout].
 */
internal fun NSMutableURLRequest.setupSocketTimeout(requestData: HttpRequestData) {
    // Darwin timeout works like a socket timeout.
    requestData.getCapabilityOrNull(HttpTimeoutCapability)?.socketTimeoutMillis?.let {
        if (it != HttpTimeoutConfig.INFINITE_TIMEOUT_MS) {
            // Timeout should be specified in seconds.
            setTimeoutInterval(it / 1000.0)
        } else {
            setTimeoutInterval(Double.MAX_VALUE)
        }
    }
}

@OptIn(UnsafeNumber::class)
internal fun handleNSError(requestData: HttpRequestData, error: NSError): Throwable = when (error.code) {
    NSURLErrorTimedOut -> SocketTimeoutException(requestData)
    else -> DarwinHttpRequestException(error)
}
