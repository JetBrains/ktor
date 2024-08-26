/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.bytestring.*
import java.nio.*

@Suppress("ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING")
public actual typealias Charset = java.nio.charset.Charset

/**
 * Find a charset by name.
 */
public actual fun Charsets.forName(name: String): Charset = Charset.forName(name)

/**
 * Check if a charset is supported by the current platform.
 */
public actual fun Charsets.isSupported(name: String): Boolean = Charset.isSupported(name)

public actual val Charset.name: String get() = name()

public actual typealias CharsetEncoder = java.nio.charset.CharsetEncoder

public actual val CharsetEncoder.charset: Charset get() = charset()

public actual fun CharsetEncoder.encodeToByteArray(input: CharSequence, fromIndex: Int, toIndex: Int): ByteArray {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    if (input is String) {
        if (fromIndex == 0 && toIndex == input.length) {
            return (input as java.lang.String).getBytes(charset())
        }
        return (input.substring(fromIndex, toIndex) as java.lang.String).getBytes(charset())
    }

    return encodeToByteArraySlow(input, fromIndex, toIndex)
}

private fun CharsetEncoder.encodeToByteArraySlow(input: CharSequence, fromIndex: Int, toIndex: Int): ByteArray {
    val result = encode(CharBuffer.wrap(input, fromIndex, toIndex))

    val existingArray = when {
        result.hasArray() && result.arrayOffset() == 0 -> result.array().takeIf { it.size == result.remaining() }
        else -> null
    }

    return existingArray ?: ByteArray(result.remaining()).also { result.get(it) }
}

internal actual fun CharsetEncoder.encodeImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int,
    dst: Sink
): Int {
    val result = encodeToByteArray(input, fromIndex, toIndex)
    dst.write(result)
    return result.size
}

internal actual fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray {
    error("Not needed on jvm")
}

public actual typealias CharsetDecoder = java.nio.charset.CharsetDecoder

public actual val CharsetDecoder.charset: Charset get() = charset()!!

public actual fun CharsetDecoder.decode(input: Source, dst: Appendable, max: Int): Int {
    if (charset == Charsets.UTF_8) {
        return input.readString().also { dst.append(it) }.length
    }

    val result = input.remaining
    dst.append(input.readByteString().decodeToString(charset))
    return result.toInt()
}

// ----------------------------------

public actual typealias Charsets = kotlin.text.Charsets

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual open class MalformedInputException
actual constructor(message: String) : java.nio.charset.MalformedInputException(0) {
    private val _message = message

    override val message: String?
        get() = _message
}
