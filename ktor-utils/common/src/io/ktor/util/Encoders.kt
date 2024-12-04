/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Empty [Encoder] that doesn't do any changes.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.Identity)
 */
public object Identity : Encoder {
    override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = source

    override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ): ByteWriteChannel = source

    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = source
}

/**
 * Content encoder.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.Encoder)
 */
public interface Encoder {
    /**
     * Launch coroutine to encode [source] bytes.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.Encoder.encode)
     */
    public fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext = EmptyCoroutineContext
    ): ByteReadChannel

    /**
     * Launch coroutine to encode [source] bytes.
     */
    public fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext = EmptyCoroutineContext
    ): ByteWriteChannel

    /**
     * Launch coroutine to decode [source] bytes.
     */
    public fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext = EmptyCoroutineContext
    ): ByteReadChannel
}
