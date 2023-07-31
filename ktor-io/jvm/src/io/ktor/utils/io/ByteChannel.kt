package io.ktor.utils.io

import java.nio.*

/**
 * Creates a channel for reading from the specified byte buffer.
 */
public fun ByteReadChannel(content: ByteBuffer): ByteReadChannel = ByteBufferChannel(content)

/**
 * Creates a buffered channel for asynchronous reading and writing of sequences of bytes.
 */
@Suppress("DEPRECATION")
public actual fun ByteChannel(autoFlush: Boolean): ByteChannel = ByteBufferChannel(autoFlush = autoFlush)

/**
 * Creates a channel for reading from the specified byte array.
 */
public actual fun ByteReadChannel(content: ByteArray, offset: Int, length: Int): ByteReadChannel =
    ByteBufferChannel(ByteBuffer.wrap(content, offset, length))

/**
 * Creates a buffered channel for asynchronous reading and writing of sequences of bytes using [close] function to close
 * a channel.
 */
@Suppress("DEPRECATION")
public fun ByteChannel(autoFlush: Boolean = false, exceptionMapper: (Throwable?) -> Throwable?): ByteChannel =
    object : ByteBufferChannel(autoFlush = autoFlush) {

        override fun close(cause: Throwable?): Boolean {
            val mappedException = exceptionMapper(cause)
            return super.close(mappedException)
        }
    }
