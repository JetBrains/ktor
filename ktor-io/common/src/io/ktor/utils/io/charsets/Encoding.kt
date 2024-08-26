package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.io.*
import kotlinx.io.IOException

public expect abstract class Charset {
    public abstract fun newEncoder(): CharsetEncoder

    public abstract fun newDecoder(): CharsetDecoder

    final override fun equals(other: Any?): Boolean

    final override fun hashCode(): Int

    final override fun toString(): String
}

/**
 * Check if a charset is supported by the current platform.
 */
public expect fun Charsets.isSupported(name: String): Boolean

/**
 * Find a charset by name.
 */
public expect fun Charsets.forName(name: String): Charset

public expect val Charset.name: String

// ----------------------------- ENCODER -------------------------------------------------------------------------------
public expect abstract class CharsetEncoder

public expect val CharsetEncoder.charset: Charset

public expect fun CharsetEncoder.encodeToByteArray(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): ByteArray

public fun CharsetEncoder.encode(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): Source = buildPacket {
    encodeToImpl(this, input, fromIndex, toIndex)
}

public fun CharsetEncoder.encode(input: CharArray, fromIndex: Int, toIndex: Int, dst: Sink) {
    encodeArrayImpl(input, fromIndex, toIndex, dst)
}

// ----------------------------- DECODER -------------------------------------------------------------------------------

public expect abstract class CharsetDecoder

/**
 * Decoder's charset it is created for.
 */
public expect val CharsetDecoder.charset: Charset

@OptIn(InternalIoApi::class)
public fun CharsetDecoder.decode(input: Source, max: Int = Int.MAX_VALUE): String = buildString(
    minOf(max.toLong(), input.buffer.size).toInt()
) {
    decode(input, this, max)
}

public expect fun CharsetDecoder.decode(input: Source, dst: Appendable, max: Int): Int

// ----------------------------- REGISTRY ------------------------------------------------------------------------------
public expect object Charsets {
    public val UTF_8: Charset
    public val ISO_8859_1: Charset
}

public expect open class MalformedInputException(message: String) : IOException

public class TooLongLineException(message: String) : MalformedInputException(message)

// ----------------------------- INTERNALS -----------------------------------------------------------------------------

internal fun CharsetEncoder.encodeArrayImpl(input: CharArray, fromIndex: Int, toIndex: Int, dst: Sink): Int {
    val length = toIndex - fromIndex
    return encodeImpl(CharArraySequence(input, fromIndex, length), 0, length, dst)
}

internal expect fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Sink): Int

internal expect fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): ByteArray

internal fun CharsetEncoder.encodeToImpl(
    destination: Sink,
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
) {
    var start = fromIndex
    if (start >= toIndex) return

    while (true) {
        val rc = encodeImpl(input, start, toIndex, destination)
        check(rc >= 0)
        start += rc

        when {
            start >= toIndex -> break
        }
    }
}
