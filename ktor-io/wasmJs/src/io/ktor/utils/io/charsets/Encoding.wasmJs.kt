/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.Buffer

public actual abstract class Charset(internal val _name: String) {
    public actual abstract fun newEncoder(): CharsetEncoder
    public actual abstract fun newDecoder(): CharsetDecoder

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Charset && (_name == other._name)
    }

    actual override fun hashCode(): Int = _name.hashCode()

    actual override fun toString(): String = _name

    public companion object {
        private fun getCharset(name: String): Charset? = when (name.replace('_', '-').lowercase()) {
            "utf8", "utf-8" -> Charsets.UTF_8
            "iso-8859-1", "latin1" -> Charsets.ISO_8859_1
            else -> null
        }

        public fun forName(name: String): Charset =
            getCharset(name) ?: throw IllegalArgumentException("Charset $name is not supported")

        public fun isSupported(charset: String): Boolean =
            getCharset(charset) != null
    }
}

/**
 * Check if a charset is supported by the current platform.
 */
public actual fun Charsets.isSupported(name: String): Boolean = Charset.isSupported(name)

/**
 * Find a charset by name.
 */
public actual fun Charsets.forName(name: String): Charset = Charset.forName(name)

public actual val Charset.name: String get() = _name

// ----------------------------- ENCODER -------------------------------------------------------------------------------
public actual abstract class CharsetEncoder(internal val _charset: Charset)

public actual val CharsetEncoder.charset: Charset
    get() = _charset

/**
 * Decoder's charset it is created for.
 */
public actual val CharsetDecoder.charset: Charset
    get() = _charset

public actual fun CharsetEncoder.encodeToByteArray(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray = buildPacket {
    encodeImpl(input, fromIndex, toIndex, this)
}.readByteArray()

public actual abstract class CharsetDecoder(internal val _charset: Charset)

@OptIn(InternalIoApi::class)
public actual fun CharsetDecoder.decode(
    input: Source,
    dst: Appendable,
    max: Int
): Int {
    val decoder = Decoder(charset.name, true)

    val count = minOf(input.buffer.size, max.toLong())
    val array = input.readByteArray(count.toInt())
    val result = try {
        decoder.decode(array)
    } catch (cause: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${cause.message ?: "no cause provided"}")
    }
    dst.append(result)
    return result.length
}

private class CharsetImpl(name: String) : Charset(name) {
    override fun newEncoder(): CharsetEncoder = object : CharsetEncoder(this) {}
    override fun newDecoder(): CharsetDecoder = object : CharsetDecoder(this) {}
}

// ----------------------------- REGISTRY ------------------------------------------------------------------------------
public actual object Charsets {
    public actual val UTF_8: Charset = CharsetImpl("UTF-8")
    public actual val ISO_8859_1: Charset = CharsetImpl("ISO-8859-1")
}

public actual open class MalformedInputException actual constructor(message: String) : IOException()

internal actual fun CharsetEncoder.encodeImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int,
    dst: Sink
): Int {
    require(fromIndex <= toIndex)
    return when (charset) {
        Charsets.ISO_8859_1 -> encodeISO88591(input, fromIndex, toIndex, dst)
        Charsets.UTF_8 -> encodeUTF8(input, fromIndex, toIndex, dst)
        else -> error { "Only UTF-8 encoding is supported in JS" }
    }
}

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

internal fun encodeUTF8(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Sink): Int {
    dst.writeString(input, fromIndex, toIndex)
    return toIndex - fromIndex
}
