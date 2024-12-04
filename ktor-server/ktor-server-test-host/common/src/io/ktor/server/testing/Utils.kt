/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.*

/**
 * [on] function receiver object
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.testing.On)
 */
public object On

/**
 * [it] function receiver object
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.testing.It)
 */
public object It

/**
 * DSL for creating a test case
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.testing.on)
 */
@Suppress("UNUSED_PARAMETER")
public inline fun on(comment: String, body: On.() -> Unit): Unit = On.body()

/**
 * DSL function for test case assertions
 */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
public inline fun On.it(description: String, body: It.() -> Unit): Unit = It.body()

internal fun CoroutineScope.configureSocketTimeoutIfNeeded(
    timeoutAttributes: HttpTimeoutConfig?,
    job: Job,
    extract: () -> Long
) {
    val socketTimeoutMillis = timeoutAttributes?.socketTimeoutMillis
    if (socketTimeoutMillis != null) {
        socketTimeoutKiller(socketTimeoutMillis, job, extract)
    }
}

internal fun CoroutineScope.socketTimeoutKiller(socketTimeoutMillis: Long, job: Job, extract: () -> Long) {
    val killJob = launch {
        var cur = extract()
        while (job.isActive) {
            delay(socketTimeoutMillis)
            val next = extract()
            if (cur == next) {
                throw NetworkSocketTimeoutException("Socket timeout elapsed")
            }
            cur = next
        }
    }
    job.invokeOnCompletion {
        killJob.cancel()
    }
}

@OptIn(InternalAPI::class)
internal fun Throwable.mapToKtor(data: HttpRequestData): Throwable = when {
    this is NetworkSocketTimeoutException -> SocketTimeoutException(data, this)
    cause?.rootCause is NetworkSocketTimeoutException -> SocketTimeoutException(data, cause?.rootCause)
    else -> this
}

// There are two SocketTimeoutException in ktor:
// * io.ktor.network.sockets.SocketTimeoutException
// * io.ktor.client.network.sockets.SocketTimeoutException
// on JVM they both are java.net.SocketTimeoutException, but on other targets it's not true
// additionally `network.sockets` exception is in `ktor-network` modules which don't have support for js/wasm target
internal expect class NetworkSocketTimeoutException(message: String) : IOException
