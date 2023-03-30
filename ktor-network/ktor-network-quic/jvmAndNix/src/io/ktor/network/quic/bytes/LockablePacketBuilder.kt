/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.utils.io.core.*
import kotlinx.atomicfu.locks.*

internal class LockablePacketBuilder {
    private var builder = BytePacketBuilder()
    private val lock = reentrantLock()

    val size get() = builder.size

    fun <T> withLock(body: (BytePacketBuilder) -> T) = lock.withLock {
        body(builder)
    }

    suspend fun <T> withLockSuspend(body: suspend (BytePacketBuilder) -> T) = lock.withLock {
        body(builder)
    }

    fun flush(body: (BytePacketBuilder) -> Unit = {}): ByteReadPacket = lock.withLock {
        flushNonBlocking(body)
    }

    fun flushNonBlocking(body: (BytePacketBuilder) -> Unit = {}): ByteReadPacket {
        body(builder)

        return builder.build().also { builder = BytePacketBuilder() }
    }
}
