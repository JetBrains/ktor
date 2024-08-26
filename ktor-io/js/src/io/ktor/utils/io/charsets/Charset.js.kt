/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import kotlinx.io.*
import org.khronos.webgl.*

/**
 * Find a charset by name.
 */
public actual fun Charsets.forName(name: String): Charset = Charset.forName(name)

/**
 * Check if a charset is supported by the current platform.
 */
public actual fun Charsets.isSupported(name: String): Boolean = Charset.isSupported(name)

public actual abstract class Charset(internal val _name: String) {
    public actual abstract fun newEncoder(): CharsetEncoder
    public actual abstract fun newDecoder(): CharsetDecoder

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as Charset

        return _name == other._name
    }

    actual override fun hashCode(): Int {
        return _name.hashCode()
    }

    actual override fun toString(): String {
        return _name
    }

    public companion object {
        public fun forName(name: String): Charset {
            if (name == "UTF-8" || name == "utf-8" || name == "UTF8" || name == "utf8") return Charsets.UTF_8
            if (name == "ISO-8859-1" || name == "iso-8859-1" ||
                name.replace('_', '-').let { it == "iso-8859-1" || it.lowercase() == "iso-8859-1" } ||
                name == "latin1" || name == "Latin1"
            ) {
                return Charsets.ISO_8859_1
            }
            throw IllegalArgumentException("Charset $name is not supported")
        }

        public fun isSupported(charset: String): Boolean = when {
            charset == "UTF-8" || charset == "utf-8" || charset == "UTF8" || charset == "utf8" -> true
            charset == "ISO-8859-1" || charset == "iso-8859-1" || charset.replace('_', '-').let {
                it == "iso-8859-1" || it.lowercase() == "iso-8859-1"
            } || charset == "latin1" -> true

            else -> false
        }
    }
}

public actual val Charset.name: String get() = _name

// -----------------------

public actual abstract class CharsetEncoder(internal val _charset: Charset)
private data class CharsetEncoderImpl(private val charset: Charset) : CharsetEncoder(charset)

public actual val CharsetEncoder.charset: Charset get() = _charset

public actual fun CharsetEncoder.encodeToByteArray(input: CharSequence, fromIndex: Int, toIndex: Int): ByteArray =
    encodeToByteArrayImpl(input, fromIndex, toIndex)

internal actual fun CharsetEncoder.encodeImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int,
    dst: Sink
): Int {
    require(fromIndex <= toIndex)
    if (charset == Charsets.ISO_8859_1) {
        return encodeISO88591(input, fromIndex, toIndex, dst)
    }

    require(charset === Charsets.UTF_8) { "Only UTF-8 encoding is supported in JS" }

    val encoder = TextEncoder() // Only UTF-8 is supported so we know that at most 6 bytes per character is used
    val result = encoder.encode(input.substring(fromIndex, toIndex))
    dst.write(result.unsafeCast<ByteArray>())
    return result.length
}

// ----------------------------------------------------------------------

public actual abstract class CharsetDecoder(internal val _charset: Charset)

private data class CharsetDecoderImpl(private val charset: Charset) : CharsetDecoder(charset)

public actual val CharsetDecoder.charset: Charset get() = _charset

internal actual fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray {
    var start = fromIndex
    if (start >= toIndex) return ByteArray(0)

    val dst = Buffer()
    val rc = encodeImpl(input, start, toIndex, dst)
    start += rc

    if (start == toIndex) {
        return dst.readByteArray()
    }

    encodeToImpl(dst, input, start, toIndex)
    return dst.readByteArray()
}

@OptIn(InternalIoApi::class)
public actual fun CharsetDecoder.decode(input: Source, dst: Appendable, max: Int): Int {
    val decoder = Decoder(charset.name, true)

    val count = minOf(input.buffer.size, max.toLong())
    val array = input.readByteArray(count.toInt()) as Int8Array
    val result = try {
        decoder.decode(array)
    } catch (cause: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${cause.message ?: "no cause provided"}")
    }
    dst.append(result)
    return result.length
}

public actual object Charsets {
    public actual val UTF_8: Charset = CharsetImpl("UTF-8")
    public actual val ISO_8859_1: Charset = CharsetImpl("ISO-8859-1")
}

private class CharsetImpl(name: String) : Charset(name) {
    override fun newEncoder(): CharsetEncoder = CharsetEncoderImpl(this)
    override fun newDecoder(): CharsetDecoder = CharsetDecoderImpl(this)
}

public actual open class MalformedInputException actual constructor(message: String) : IOException(message)
