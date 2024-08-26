/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CryptoKt")

package io.ktor.util

import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.*

private val digits = "0123456789abcdef".toCharArray()

internal const val NONCE_SIZE_IN_BYTES = 16

/**
 * Encode [bytes] as a HEX string with no spaces, newlines and `0x` prefixes.
 */
public fun hex(bytes: ByteArray): String {
    val result = CharArray(bytes.size * 2)
    var resultIndex = 0
    val digits = digits

    for (element in bytes) {
        val b = element.toInt() and 0xff
        result[resultIndex++] = digits[b shr 4]
        result[resultIndex++] = digits[b and 0x0f]
    }

    return result.concatToString()
}

/**
 * Decode bytes from HEX string. It should be no spaces and `0x` prefixes.
 */
public fun hex(s: String): ByteArray {
    val result = ByteArray(s.length / 2)
    for (idx in result.indices) {
        val srcIdx = idx * 2
        val high = s[srcIdx].toString().toInt(16) shl 4
        val low = s[srcIdx + 1].toString().toInt(16)
        result[idx] = (high or low).toByte()
    }

    return result
}

/**
 * Generates a nonce string. Could block if the system's entropy source is empty
 */
public expect fun generateNonce(): String

/**
 * Generates a nonce bytes of [size]. Could block if the system's entropy source is empty
 */
public fun generateNonce(size: Int): ByteArray = buildPacket {
    while (this.size < size) {
        writeText(generateNonce())
    }
}.readByteArray(size)

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public expect fun sha1(bytes: ByteArray): ByteArray

/**
 * Create [Digest] from specified hash [name].
 */
@Suppress("FunctionName")
public expect fun Digest(name: String): Digest

/**
 * Stateful digest class specified to calculate digest.
 */
public interface Digest {
    /**
     * Add [bytes] to digest value.
     */
    public operator fun plusAssign(bytes: ByteArray)

    /**
     * Reset [Digest] state.
     */
    public fun reset()

    /**
     * Calculate digest bytes.
     */
    public suspend fun build(): ByteArray
}

/**
 * Calculate digest from current state and specified [bytes].
 */
@InternalAPI
public suspend fun Digest.build(bytes: ByteArray): ByteArray {
    this += bytes
    return build()
}

/**
 * Calculate digest from current state and specified [string].
 */
@InternalAPI
public suspend fun Digest.build(string: String, charset: Charset = Charsets.UTF_8): ByteArray {
    this += string.toByteArray(charset)
    return build()
}
