package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.js.*
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as Charset

        if (_name != other._name) return false

        return true
    }

    override fun hashCode(): Int {
        return _name.hashCode()
    }

    override fun toString(): String {
        return _name
    }

    public companion object {
        @Suppress("LocalVariableName")
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

@Suppress("DEPRECATION")
internal actual fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Buffer): Int {
    require(fromIndex <= toIndex)
    if (charset == Charsets.ISO_8859_1) {
        return encodeISO88591(input, fromIndex, toIndex, dst)
    }

    require(charset === Charsets.UTF_8) { "Only UTF-8 encoding is supported in JS" }

    val encoder = TextEncoder() // Only UTF-8 is supported so we know that at most 6 bytes per character is used
    var start = fromIndex
    var dstRemaining = dst.writeRemaining

    while (start < toIndex && dstRemaining > 0) {
        val numChars = minOf(toIndex - start, dstRemaining / 6).coerceAtLeast(1)
        val dropLastChar = input[start + numChars - 1].isHighSurrogate()
        val endIndexExclusive = when {
            dropLastChar && numChars == 1 -> start + 2
            dropLastChar -> start + numChars - 1
            else -> start + numChars
        }

        val array1 = encoder.encode(input.substring(start, endIndexExclusive))
        if (array1.length > dstRemaining) break
        dst.writeFully(array1)
        start = endIndexExclusive
        dstRemaining -= array1.length
    }

    return start - fromIndex
}

@Suppress("DEPRECATION")
public actual fun CharsetEncoder.encodeUTF8(input: ByteReadPacket, dst: Output) {
    require(charset === Charsets.UTF_8)
    // we only support UTF-8 so as far as input is UTF-8 encoded string then we simply copy bytes
    dst.writePacket(input)
}

@Suppress("DEPRECATION")
internal actual fun CharsetEncoder.encodeComplete(dst: Buffer): Boolean = true

// ----------------------------------------------------------------------

public actual abstract class CharsetDecoder(internal val _charset: Charset)

private data class CharsetDecoderImpl(private val charset: Charset) : CharsetDecoder(charset)

public actual val CharsetDecoder.charset: Charset get() = _charset

@Suppress("DEPRECATION")
internal actual fun CharsetDecoder.decodeBuffer(
    input: Buffer,
    out: Appendable,
    lastBuffer: Boolean,
    max: Int
): Int {
    if (max == 0) return 0

    val decoder = Decoder(charset.name)
    val copied: Int

    input.readDirectInt8Array { view ->
        val result = view.decodeBufferImpl(decoder, max)
        out.append(result.charactersDecoded)
        copied = result.bytesConsumed

        result.bytesConsumed
    }

    return copied
}

@Suppress("DEPRECATION")
internal actual fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray {
    var start = fromIndex
    if (start >= toIndex) return EmptyByteArray
    val single = ChunkBuffer.Pool.borrow()

    try {
        val rc = encodeImpl(input, start, toIndex, single)
        start += rc
        if (start == toIndex) {
            val result = ByteArray(single.readRemaining)
            single.readFully(result)
            return result
        }

        return buildPacket {
            appendSingleChunk(single.duplicate())
            encodeToImpl(this, input, start, toIndex)
        }.readBytes()
    } finally {
        single.release(ChunkBuffer.Pool)
    }
}

@Suppress("DEPRECATION")
public actual fun CharsetDecoder.decode(input: Input, dst: Appendable, max: Int): Int {
    val decoder = Decoder(charset.name, true)
    var charactersCopied = 0

    // use decode stream while we have remaining characters count > buffer size in bytes
    // it is much faster than using decodeBufferImpl
    input.takeWhileSize { buffer ->
        val rem = max - charactersCopied
        val bufferSize = buffer.readRemaining
        if (rem < bufferSize) return@takeWhileSize 0

        buffer.readDirectInt8Array { view ->
            val decodedText = decodeWrap {
                decoder.decodeStream(view, stream = true)
            }
            dst.append(decodedText)
            charactersCopied += decodedText.length
            view.byteLength
        }

        when {
            charactersCopied == max -> {
                val tail = try {
                    decoder.decode()
                } catch (_: dynamic) {
                    ""
                }

                if (tail.isNotEmpty()) {
                    // if we have a trailing byte then we can't handle this chunk via fast-path
                    // because we don't know how many bytes in the end we need to preserve
                    buffer.rewind(bufferSize)
                }
                0
            }
            charactersCopied < max -> MAX_CHARACTERS_SIZE_IN_BYTES
            else -> 0
        }
    }

    if (charactersCopied < max) {
        var size = 1
        input.takeWhileSize(1) { buffer ->
            val rc = buffer.readDirectInt8Array { view ->
                val result = view.decodeBufferImpl(decoder, max - charactersCopied)
                dst.append(result.charactersDecoded)
                charactersCopied += result.charactersDecoded.length
                result.bytesConsumed
            }
            when {
                rc > 0 -> size = 1
                size == MAX_CHARACTERS_SIZE_IN_BYTES -> size = 0
                else -> size++
            }

            size
        }
    }

    return charactersCopied
}

@Suppress("DEPRECATION")
public actual fun CharsetDecoder.decodeExactBytes(input: Input, inputLength: Int): String {
    if (inputLength == 0) return ""
    if (input.headRemaining >= inputLength) {
        val decoder = Decoder(charset._name, true)

        val head = input.head
        val view = input.headMemory.view

        val text = decodeWrap {
            val subView: ArrayBufferView = when {
                head.readPosition == 0 && inputLength == view.byteLength -> view
                else -> DataView(view.buffer, view.byteOffset + head.readPosition, inputLength)
            }

            decoder.decode(subView)
        }

        input.discardExact(inputLength)
        return text
    }

    return decodeExactBytesSlow(input, inputLength)
}

// -----------------------------------------------------------

public actual object Charsets {
    public actual val UTF_8: Charset = CharsetImpl("UTF-8")
    public actual val ISO_8859_1: Charset = CharsetImpl("ISO-8859-1")
}

private data class CharsetImpl(val name: String) : Charset(name) {
    override fun newEncoder(): CharsetEncoder = CharsetEncoderImpl(this)
    override fun newDecoder(): CharsetDecoder = CharsetDecoderImpl(this)
}

public actual open class MalformedInputException actual constructor(message: String) : Throwable(message)

@Suppress("DEPRECATION")
private fun CharsetDecoder.decodeExactBytesSlow(input: Input, inputLength: Int): String {
    val decoder = Decoder(charset.name, true)
    var inputRemaining = inputLength
    val sb = StringBuilder(inputLength)

    decodeWrap {
        input.takeWhileSize(6) { buffer ->
            val chunkSize = buffer.readRemaining
            val size = minOf(chunkSize, inputRemaining)
            val text = when {
                buffer.readPosition == 0 && buffer.memory.view.byteLength == size -> decoder.decodeStream(
                    buffer.memory.view,
                    true
                )
                else -> decoder.decodeStream(
                    Int8Array(
                        buffer.memory.view.buffer,
                        buffer.memory.view.byteOffset + buffer.readPosition,
                        size
                    ),
                    true
                )
            }
            sb.append(text)

            buffer.discardExact(size)
            inputRemaining -= size

            if (inputRemaining > 0) 6 else 0
        }

        if (inputRemaining > 0) {
            input.takeWhile { buffer ->
                val chunkSize = buffer.readRemaining
                val size = minOf(chunkSize, inputRemaining)
                val text = when {
                    buffer.readPosition == 0 && buffer.memory.view.byteLength == size -> {
                        decoder.decode(buffer.memory.view)
                    }
                    else -> decoder.decodeStream(
                        Int8Array(
                            buffer.memory.view.buffer,
                            buffer.memory.view.byteOffset + buffer.readPosition,
                            size
                        ),
                        true
                    )
                }
                sb.append(text)
                buffer.discardExact(size)
                inputRemaining -= size
                true
            }
        }

        sb.append(decoder.decode())
    }

    if (inputRemaining > 0) {
        throw EOFException(
            "Not enough bytes available: had only ${inputLength - inputRemaining} instead of $inputLength"
        )
    }
    return sb.toString()
}
