/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import platform.windows.*
import platform.winhttp.*

@OptIn(ExperimentalForeignApi::class)
internal typealias WinHttpStatusHandler = (statusInfo: LPVOID?, statusInfoLength: DWORD) -> Unit

internal class WinHttpConnect @OptIn(ExperimentalForeignApi::class) constructor(
    private val hConnect: COpaquePointer
) : Closeable {

    private val closed = atomic(false)

    @OptIn(ExperimentalForeignApi::class)
    val handlers = mutableMapOf<UInt, WinHttpStatusHandler>()

    val isClosed: Boolean
        get() = closed.value

    /**
     * Opens an HTTP request to the target server.
     * @param method is request method.
     * @param url is request URL.
     * @param chunkedMode is request body chunking mode.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun openRequest(
        method: HttpMethod,
        url: Url,
        httpVersion: String?
    ): COpaquePointer? {
        var openFlags = WINHTTP_FLAG_ESCAPE_DISABLE or
            WINHTTP_FLAG_ESCAPE_DISABLE_QUERY or
            WINHTTP_FLAG_NULL_CODEPAGE

        if (url.protocol.isSecure()) {
            openFlags = openFlags or WINHTTP_FLAG_SECURE
        }

        return WinHttpOpenRequest(
            hConnect,
            method.value,
            url.fullPath,
            httpVersion,
            WINHTTP_NO_REFERER,
            WINHTTP_DEFAULT_ACCEPT_TYPES,
            openFlags.convert()
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    fun on(status: WinHttpCallbackStatus, handler: WinHttpStatusHandler) {
        handlers[status.value] = handler
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return

        handlers.clear()
        WinHttpCloseHandle(hConnect)
    }

    companion object {
        private const val WINHTTP_FLAG_AUTOMATIC_CHUNKING = 0x00000200
        private val WINHTTP_NO_REFERER = null
        private val WINHTTP_DEFAULT_ACCEPT_TYPES = null
    }
}
