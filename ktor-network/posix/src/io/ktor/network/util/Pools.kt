/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.network.sockets.*
import io.ktor.utils.io.pool.*

/**
 * Byte array pool for UDP datagrams
 */
internal val DefaultDatagramByteArrayPool: ObjectPool<ByteArray> =
    object : DefaultPool<ByteArray>(2048) {
        override fun produceInstance(): ByteArray = ByteArray(MAX_DATAGRAM_SIZE)
    }
