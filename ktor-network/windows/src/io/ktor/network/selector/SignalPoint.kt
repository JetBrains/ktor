/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal class SignalPoint : Closeable {

    val event: COpaquePointer?

    init {
        initSocketsIfNeeded()

        event = WSACreateEvent()
    }

    fun check() {
        WSAResetEvent(event)
    }

    fun signal() {
        WSASetEvent(event)
    }

    override fun close() {
        WSACloseEvent(event)
    }
}
