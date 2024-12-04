/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.pool.*
import java.nio.*
import java.nio.charset.*

/**
 * Moves bytes from `this` buffer to the [destination] buffer
 *
 * @param destination is the buffer to copy bytes to
 * @param limit is an optional parameter specifying maximum number of bytes to be moved
 * @return number of bytes moved
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.moveTo)
 */
public fun ByteBuffer.moveTo(destination: ByteBuffer, limit: Int = Int.MAX_VALUE): Int {
    val size = minOf(limit, remaining(), destination.remaining())
    if (size == remaining()) {
        destination.put(this)
    } else {
        val l = limit()
        limit(position() + size)
        destination.put(this)
        limit(l)
    }
    return size
}

/**
 * Moves bytes from `this` buffer into newly created [ByteArray] and returns it
 */
public fun ByteBuffer.moveToByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}

/**
 * Decodes a string from `this` buffer with the specified [charset]
 */
public fun ByteBuffer.decodeString(charset: Charset = Charsets.UTF_8): String {
    return charset.decode(this).toString()
}

/**
 * Moves all bytes in `this` buffer to a newly created buffer with the optionally specified [size]
 */
public fun ByteBuffer.copy(size: Int = remaining()): ByteBuffer {
    return ByteBuffer.allocate(size).apply {
        this@copy.slice().moveTo(this@apply)
        clear()
    }
}

/**
 * Moves all bytes in `this` buffer to a newly created buffer with the optionally specified [size] by allocating it from the given [pool]
 */
public fun ByteBuffer.copy(pool: ObjectPool<ByteBuffer>, size: Int = remaining()): ByteBuffer = pool.borrow().apply {
    limit(size)
    this@copy.slice().moveTo(this)
    flip()
}
