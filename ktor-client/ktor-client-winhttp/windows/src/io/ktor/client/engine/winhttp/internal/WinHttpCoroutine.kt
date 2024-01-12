/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.winhttp.*
import kotlin.coroutines.*

@OptIn(ExperimentalForeignApi::class)
internal suspend inline fun <T> Closeable.closeableCoroutine(
    state: WinHttpConnect,
    errorMessage: String,
    crossinline block: (CancellableContinuation<T>) -> Unit
): T = suspendCancellableCoroutine { continuation ->
    if (!continuation.isActive) {
        close()
        return@suspendCancellableCoroutine
    }

    continuation.invokeOnCancellation {
        close()
    }

    state.handlers[WinHttpCallbackStatus.RequestError.value] = { statusInfo, _ ->
        if (continuation.isActive) {
            val result = statusInfo!!.reinterpret<WINHTTP_ASYNC_RESULT>().pointed
            continuation.resumeWithException(getWinHttpException(errorMessage, result.dwError))
        } else {
            close()
        }
    }

    state.handlers[WinHttpCallbackStatus.SecureFailure.value] = { statusInfo, _ ->
        if (continuation.isActive) {
            val securityCode = statusInfo!!.reinterpret<UIntVar>().pointed.value
            continuation.resumeWithException(getWinHttpException(errorMessage, securityCode))
        } else {
            close()
        }
    }

    try {
        block(continuation)
    } catch (cause: Throwable) {
        if (continuation.isActive) {
            continuation.resumeWithException(cause)
        } else {
            close()
        }
    }
}
