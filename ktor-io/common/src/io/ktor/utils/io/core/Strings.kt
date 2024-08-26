package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.*
import kotlinx.io.*
import kotlin.math.*

public fun String.toByteArray(charset: Charset = Charsets.UTF_8): ByteArray {
    if (charset == Charsets.UTF_8) return encodeToByteArray(throwOnInvalidSequence = true)

    return charset.newEncoder().encodeToByteArray(this, 0, length)
}

/**
 * Create an instance of [String] from the specified [bytes] range starting at [offset] and bytes [length]
 * interpreting characters in the specified [charset].
 */
@Deprecated(
    "Use decodeToString instead",
    ReplaceWith("bytes.decodeToString(offset, offset + length)"),
    DeprecationLevel.WARNING
)
public fun String(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
    charset: Charset = Charsets.UTF_8
): String = when (charset) {
    Charsets.UTF_8 -> bytes.decodeToString(offset, offset + length)
    else -> buildPacket {
        writeFully(bytes, offset, length)
    }.readText(charset)
}

/**
 * Read exactly [n] bytes (consumes all remaining if [n] is not specified but up to [Int.MAX_VALUE] bytes).
 * Does fail if not enough bytes remaining.
 */
@Deprecated(
    "Use readByteArray instead",
    ReplaceWith("this.readByteArray()", "kotlinx.io.readByteArray"),
)
public fun Source.readBytes(): ByteArray = readByteArray()

@Deprecated(
    "Use readByteArray instead",
    ReplaceWith("this.readByteArray(count)"),
)
public fun Source.readBytes(count: Int): ByteArray = readByteArray(count)

/**
 * Reads at most [max] characters decoding bytes with specified [charset]. Extra character bytes will remain unconsumed
 * @return a decoded string
 */
@OptIn(InternalIoApi::class)
public fun Source.readText(charset: Charset = Charsets.UTF_8, max: Int = Int.MAX_VALUE): String {
    if (charset == Charsets.UTF_8) {
        if (max == Int.MAX_VALUE) return readString()
        val count = min(buffer.size, max.toLong())
        return readString(count)
    }

    return charset.newDecoder().decode(this, max)
}

/**
 * Read exactly [n] characters interpreting bytes in the specified [charset].
 */
@Deprecated(
    "Use readTextExactCharacters instead.",
    ReplaceWith("readTextExactCharacters(n, charset)")
)
public fun Source.readTextExact(charset: Charset = Charsets.UTF_8, n: Int): String {
    return readTextExactCharacters(n, charset)
}

/**
 * Read exactly [charactersCount] characters interpreting bytes in the specified [charset].
 */
public fun Source.readTextExactCharacters(charactersCount: Int, charset: Charset = Charsets.UTF_8): String {
    val s = readText(charset, charactersCount)
    if (s.length < charactersCount) {
        prematureEndOfStreamToReadChars(charactersCount)
    }
    return s
}

/**
 * Writes [text] characters in range \[[fromIndex] .. [toIndex]) with the specified [charset]
 */
public fun Sink.writeText(
    text: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = text.length,
    charset: Charset = Charsets.UTF_8
) {
    if (charset === Charsets.UTF_8) {
        return writeString(text.toString(), fromIndex, toIndex)
    }

    charset.newEncoder().encodeToImpl(this, text, fromIndex, toIndex)
}

/**
 * Writes [text] characters in range \[[fromIndex] .. [toIndex]) with the specified [charset]
 */
public fun Sink.writeText(
    text: CharArray,
    fromIndex: Int = 0,
    toIndex: Int = text.size,
    charset: Charset = Charsets.UTF_8
) {
    if (charset === Charsets.UTF_8) {
        val string = text.concatToString(fromIndex, fromIndex + toIndex)
        return writeString(string, 0, toIndex - fromIndex)
    }

    charset.newEncoder().encode(text, fromIndex, toIndex, this)
}

private fun prematureEndOfStreamToReadChars(charactersCount: Int): Nothing =
    throw EOFException("Not enough input bytes to read $charactersCount characters.")
