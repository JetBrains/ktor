/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.cio

import io.ktor.utils.io.pool.*
import java.nio.*

internal const val DEFAULT_BUFFER_SIZE = 4098
internal const val DEFAULT_KTOR_POOL_SIZE = 2048

/**
 * The default ktor byte buffer pool
 */
public val KtorDefaultPool: ObjectPool<ByteBuffer> = ByteBufferPool(DEFAULT_KTOR_POOL_SIZE, DEFAULT_BUFFER_SIZE)
