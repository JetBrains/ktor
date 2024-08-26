/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.pool

private const val DEFAULT_POOL_ARRAY_SIZE = 4096
private const val DEFAULT_POOL_CAPACITY = 128

public val ByteArrayPool: ObjectPool<ByteArray> = object : DefaultPool<ByteArray>(DEFAULT_POOL_CAPACITY) {
    override fun produceInstance(): ByteArray = ByteArray(DEFAULT_POOL_ARRAY_SIZE)
}
