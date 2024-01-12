/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import kotlinx.cinterop.*
import platform.windows.*
import platform.windows.HINTERNET
import platform.winhttp.*

@OptIn(ExperimentalForeignApi::class)
internal enum class WinHttpCallbackStatus(val value: UInt) {
    SecureFailure(WINHTTP_CALLBACK_STATUS_SECURE_FAILURE.convert<UInt>()),
    HeadersAvailable(WINHTTP_CALLBACK_STATUS_HEADERS_AVAILABLE.convert<UInt>()),
    DataAvailable(WINHTTP_CALLBACK_STATUS_DATA_AVAILABLE.convert<UInt>()),
    ReadComplete(WINHTTP_CALLBACK_STATUS_READ_COMPLETE.convert<UInt>()),
    WriteComplete(WINHTTP_CALLBACK_STATUS_WRITE_COMPLETE.convert<UInt>()),
    RequestError(WINHTTP_CALLBACK_STATUS_REQUEST_ERROR.convert<UInt>()),
    SendRequestComplete(WINHTTP_CALLBACK_STATUS_SENDREQUEST_COMPLETE.convert<UInt>()),
    CloseComplete(WINHTTP_CALLBACK_STATUS_CLOSE_COMPLETE.convert<UInt>())
}

@OptIn(ExperimentalForeignApi::class)
internal fun winHttpCallback(
    @Suppress("UNUSED_PARAMETER")
    hInternet: HINTERNET?,
    dwContext: DWORD_PTR,
    dwStatus: DWORD,
    statusInfo: LPVOID?,
    statusInfoLength: DWORD
) {
    val contextPtr = dwContext.toLong().toCPointer<COpaque>() ?: return

    val connect = contextPtr.asStableRef<WinHttpConnect>().get()
    if (connect.isClosed) {
        return
    }

    connect.handlers[dwStatus]?.invoke(statusInfo, statusInfoLength)
}
