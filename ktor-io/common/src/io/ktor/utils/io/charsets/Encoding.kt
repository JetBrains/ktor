package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*

public expect abstract class Charset {
    public abstract fun newEncoder(): CharsetEncoder

    public abstract fun newDecoder(): CharsetDecoder
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

@Suppress("DEPRECATION")
public expect fun CharsetEncoder.encodeUTF8(input: ByteReadPacket, dst: Output)

public fun CharsetEncoder.encode(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): ByteReadPacket = buildPacket {
    encodeToImpl(this, input, fromIndex, toIndex)
}

public fun CharsetEncoder.encodeUTF8(input: ByteReadPacket): ByteReadPacket = buildPacket {
    encodeUTF8(input, this)
}

@Suppress("DEPRECATION")
public fun CharsetEncoder.encode(input: CharArray, fromIndex: Int, toIndex: Int, dst: Output) {
    var start = fromIndex

    if (start >= toIndex) return
    dst.writeWhileSize(1) { view: Buffer ->
        val rc = encodeArrayImpl(input, start, toIndex, view)
        check(rc >= 0)
        start += rc

        when {
            start >= toIndex -> 0
            rc == 0 -> 8
            else -> 1
        }
    }

    encodeCompleteImpl(dst)
}

// ----------------------------- DECODER -------------------------------------------------------------------------------

public expect abstract class CharsetDecoder

/**
 * Decoder's charset it is created for.
 */
public expect val CharsetDecoder.charset: Charset

@Suppress("DEPRECATION")
public fun CharsetDecoder.decode(input: Input, max: Int = Int.MAX_VALUE): String =
    buildString(minOf(max.toLong(), input.sizeEstimate()).toInt()) {
        decode(input, this, max)
    }

@Suppress("DEPRECATION")
public expect fun CharsetDecoder.decode(input: Input, dst: Appendable, max: Int): Int

@Suppress("DEPRECATION")
public expect fun CharsetDecoder.decodeExactBytes(input: Input, inputLength: Int): String

// ----------------------------- REGISTRY ------------------------------------------------------------------------------
public expect object Charsets {
    public val UTF_8: Charset
    public val ISO_8859_1: Charset
}

public expect open class MalformedInputException(message: String) : Throwable

public class TooLongLineException(message: String) : MalformedInputException(message)

// ----------------------------- INTERNALS -----------------------------------------------------------------------------

@Suppress("DEPRECATION")
internal fun CharsetEncoder.encodeArrayImpl(input: CharArray, fromIndex: Int, toIndex: Int, dst: Buffer): Int {
    val length = toIndex - fromIndex
    return encodeImpl(CharArraySequence(input, fromIndex, length), 0, length, dst)
}

@Suppress("DEPRECATION")
internal expect fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Buffer): Int

@Suppress("DEPRECATION")
internal expect fun CharsetEncoder.encodeComplete(dst: Buffer): Boolean

@Suppress("DEPRECATION")
internal expect fun CharsetDecoder.decodeBuffer(
    input: Buffer,
    out: Appendable,
    lastBuffer: Boolean,
    max: Int = Int.MAX_VALUE
): Int

internal expect fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = input.length
): ByteArray

@Suppress("DEPRECATION")
internal fun Input.sizeEstimate(): Long = when (this) {
    is ByteReadPacket -> remaining
    else -> maxOf(remaining, 16)
}

@Suppress("DEPRECATION")
private fun CharsetEncoder.encodeCompleteImpl(dst: Output): Int {
    var size = 1
    var bytesWritten = 0

    dst.writeWhile { view ->
        val before = view.writeRemaining
        if (encodeComplete(view)) {
            size = 0
        } else {
            size++
        }
        bytesWritten += before - view.writeRemaining
        size > 0
    }

    return bytesWritten
}

@Suppress("DEPRECATION")
internal fun CharsetEncoder.encodeToImpl(
    destination: Output,
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): Int {
    var start = fromIndex
    if (start >= toIndex) return 0

    var bytesWritten = 0

    destination.writeWhileSize(1) { view: Buffer ->
        val before = view.writeRemaining
        val rc = encodeImpl(input, start, toIndex, view)
        check(rc >= 0)
        start += rc
        bytesWritten += before - view.writeRemaining

        when {
            start >= toIndex -> 0
            rc == 0 -> 8
            else -> 1
        }
    }

    bytesWritten += encodeCompleteImpl(destination)
    return bytesWritten
}
