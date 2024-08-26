/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.network.sockets.*
import io.ktor.utils.io.pool.*
import java.nio.*

internal const val DEFAULT_BYTE_BUFFER_POOL_SIZE: Int = 4096

internal const val DEFAULT_BYTE_BUFFER_BUFFER_SIZE: Int = 4096

/**
 * Byte buffer pool for general-purpose buffers.
 */
public val DefaultByteBufferPool: ObjectPool<ByteBuffer> =
    DirectByteBufferPool(DEFAULT_BYTE_BUFFER_POOL_SIZE, DEFAULT_BYTE_BUFFER_BUFFER_SIZE)

/**
 * Byte buffer pool for UDP datagrams
 */
public val DefaultDatagramByteBufferPool: ObjectPool<ByteBuffer> =
    DirectByteBufferPool(2048, MAX_DATAGRAM_SIZE)
