/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * A request/response content encoder.
 */
public interface ContentEncoder : Encoder {
    /**
     * Encoder identifier to use in http headers.
     */
    public val name: String

    /**
     * Provides an estimation for the compressed length based on the originalLength or return null if it's impossible.
     */
    public fun predictCompressedLength(contentLength: Long): Long? = null
}

/**
 * Implementation of [ContentEncoder] using gzip algorithm
 */
public expect object GZipEncoder : ContentEncoder {
    override val name: String

    override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel

    override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ): ByteWriteChannel

    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel
}

/**
 * Implementation of [ContentEncoder] using deflate algorithm
 */
public expect object DeflateEncoder : ContentEncoder {
    override val name: String

    override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel

    override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ): ByteWriteChannel

    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel
}

/**
 * Implementation of [ContentEncoder] using identity algorithm
 */
public object IdentityEncoder : ContentEncoder, Encoder by Identity {
    override val name: String = "identity"

    override fun predictCompressedLength(contentLength: Long): Long = contentLength
}
