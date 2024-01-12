/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.cinterop.*
import platform.iconv.*
import platform.posix.*

public actual object Charsets {
    public actual val UTF_8: Charset = CharsetIconv("UTF-8")
    public actual val ISO_8859_1: Charset = CharsetIconv("ISO-8859-1")
    internal val UTF_16: Charset = CharsetIconv(platformUtf16)
}

internal actual fun findCharset(name: String): Charset {
    if (name == "UTF-8" || name == "utf-8" || name == "UTF8" || name == "utf8") return Charsets.UTF_8
    if (name == "ISO-8859-1" || name == "iso-8859-1" || name == "ISO_8859_1") return Charsets.ISO_8859_1
    if (name == "UTF-16" || name == "utf-16" || name == "UTF16" || name == "utf16") return Charsets.UTF_16

    return CharsetIconv(name)
}

@OptIn(ExperimentalForeignApi::class)
private class CharsetIconv(name: String) : Charset(name) {
    init {
        val v = iconv_open(name, "UTF-8")
        checkErrors(v, name)
        iconv_close(v)
    }

    override fun newEncoder(): CharsetEncoder = CharsetEncoderImpl(this)
    override fun newDecoder(): CharsetDecoder = CharsetDecoderImpl(this)
}

internal fun iconvCharsetName(name: String) = when (name) {
    "UTF-16" -> platformUtf16
    else -> name
}

@OptIn(ExperimentalForeignApi::class)
private val negativePointer = (-1L).toCPointer<IntVar>()

@OptIn(ExperimentalForeignApi::class)
internal fun checkErrors(iconvOpenResults: COpaquePointer?, charset: String) {
    if (iconvOpenResults == null || iconvOpenResults === negativePointer) {
        throw IllegalArgumentException("Failed to open iconv for charset $charset with error code ${posix_errno()}")
    }
}

@Suppress("DEPRECATION")
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Buffer): Int {
    val length = toIndex - fromIndex
    if (length == 0) return 0

    val chars = input.substring(fromIndex, toIndex).toCharArray()
    val charset = iconvCharsetName(_charset._name)
    val cd: COpaquePointer? = iconv_open(charset, platformUtf16)
    checkErrors(cd, charset)

    var charsConsumed = 0
    try {
        dst.writeDirect { buffer ->
            chars.usePinned { pinned ->
                memScoped {
                    val inbuf = alloc<CPointerVar<ByteVar>>()
                    val outbuf = alloc<CPointerVar<ByteVar>>()
                    val inbytesleft = alloc<size_tVar>()
                    val outbytesleft = alloc<size_tVar>()
                    val dstRemaining = dst.writeRemaining.convert<size_t>()

                    inbuf.value = pinned.addressOf(0).reinterpret()
                    outbuf.value = buffer
                    inbytesleft.value = (length * 2).convert<size_t>()
                    outbytesleft.value = dstRemaining

                    val convertResult = iconv(cd, inbuf.ptr, inbytesleft.ptr, outbuf.ptr, outbytesleft.ptr).toULong()
                    if (convertResult == MAX_SIZE.toULong()) {
                        checkIconvResult(posix_errno())
                    }

                    charsConsumed = ((length * 2).convert<size_t>() - inbytesleft.value).toInt() / 2
                    (dstRemaining - outbytesleft.value).toInt()
                }
            }
        }

        return charsConsumed
    } finally {
        iconv_close(cd)
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
@Suppress("DEPRECATION")
public actual fun CharsetDecoder.decode(input: Input, dst: Appendable, max: Int): Int {
    val charset = iconvCharsetName(charset.name)
    val cd = iconv_open(platformUtf16, charset)
    checkErrors(cd, charset)
    val chars = CharArray(8192)
    var copied = 0

    try {
        var readSize = 1

        chars.usePinned { pinned ->
            memScoped {
                val inbuf = alloc<CPointerVar<ByteVar>>()
                val outbuf = alloc<CPointerVar<ByteVar>>()
                val inbytesleft = alloc<size_tVar>()
                val outbytesleft = alloc<size_tVar>()

                val buffer = pinned.addressOf(0).reinterpret<ByteVar>()

                input.takeWhileSize { srcView ->
                    val rem = max - copied
                    if (rem == 0) return@takeWhileSize 0

                    var written: Int
                    var read: Int

                    srcView.readDirect { src ->
                        val length = srcView.readRemaining.convert<size_t>()
                        val dstRemaining = (minOf(chars.size, rem) * 2).convert<size_t>()

                        inbuf.value = src
                        outbuf.value = buffer
                        inbytesleft.value = length
                        outbytesleft.value = dstRemaining

                        val convertResult = iconv(cd, inbuf.ptr, inbytesleft.ptr, outbuf.ptr, outbytesleft.ptr)
                            .toULong()

                        if (convertResult == MAX_SIZE.toULong()) {
                            checkIconvResult(posix_errno())
                        }

                        read = (length - inbytesleft.value).toInt()
                        written = (dstRemaining - outbytesleft.value).toInt() / 2

                        read
                    }

                    if (read == 0) {
                        readSize++
                    } else {
                        readSize = 1

                        repeat(written) {
                            dst.append(chars[it])
                        }
                        copied += written
                    }

                    readSize
                }
            }
        }

        return copied
    } finally {
        iconv_close(cd)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun CharsetDecoder.decodeBuffer(
    input: Buffer,
    out: Appendable,
    lastBuffer: Boolean,
    max: Int
): Int {
    if (!input.canRead() || max == 0) {
        return 0
    }

    val charset = iconvCharsetName(charset.name)
    val cd = iconv_open(platformUtf16, charset)
    checkErrors(cd, charset)

    var charactersCopied = 0
    try {
        input.readDirect { ptr ->
            val size = input.readRemaining
            val result = CharArray(size)

            val bytesLeft = memScoped {
                result.usePinned { pinnedResult ->
                    val inbuf = alloc<CPointerVar<ByteVar>>()
                    val outbuf = alloc<CPointerVar<ByteVar>>()
                    val inbytesleft = alloc<size_tVar>()
                    val outbytesleft = alloc<size_tVar>()

                    inbuf.value = ptr
                    outbuf.value = pinnedResult.addressOf(0).reinterpret()
                    inbytesleft.value = size.convert()
                    outbytesleft.value = (size * 2).convert()

                    val convResult = iconv(cd, inbuf.ptr, inbytesleft.ptr, outbuf.ptr, outbytesleft.ptr).toULong()
                    if (convResult == MAX_SIZE.toULong()) {
                        checkIconvResult(posix_errno())
                    }

                    charactersCopied += (size * 2 - outbytesleft.value.convert<Int>()) / 2
                    inbytesleft.value.convert<Int>()
                }
            }

            repeat(charactersCopied) { index ->
                out.append(result[index])
            }

            size - bytesLeft
        }

        return charactersCopied
    } finally {
        iconv_close(cd)
    }
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
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
public actual fun CharsetDecoder.decodeExactBytes(input: Input, inputLength: Int): String {
    if (inputLength == 0) return ""

    val charset = iconvCharsetName(charset.name)
    val cd = iconv_open(platformUtf16, charset)
    checkErrors(cd, charset)

    val chars = CharArray(inputLength)
    var charsCopied = 0
    var bytesConsumed = 0

    try {
        var readSize = 1

        chars.usePinned { pinned ->
            memScoped {
                val inbuf = alloc<CPointerVar<ByteVar>>()
                val outbuf = alloc<CPointerVar<ByteVar>>()
                val inbytesleft = alloc<size_tVar>()
                val outbytesleft = alloc<size_tVar>()

                input.takeWhileSize { srcView ->
                    val rem = inputLength - charsCopied
                    if (rem == 0) return@takeWhileSize 0

                    var written: Int
                    var read: Int

                    srcView.readDirect { src ->
                        val length = minOf(srcView.readRemaining, inputLength - bytesConsumed).convert<size_t>()
                        val dstRemaining = (rem * 2).convert<size_t>()

                        inbuf.value = src
                        outbuf.value = pinned.addressOf(charsCopied).reinterpret()
                        inbytesleft.value = length
                        outbytesleft.value = dstRemaining

                        val convResult = iconv(cd, inbuf.ptr, inbytesleft.ptr, outbuf.ptr, outbytesleft.ptr)
                        if (convResult == MAX_SIZE.toULong()) {
                            checkIconvResult(posix_errno())
                        }

                        read = (length - inbytesleft.value).toInt()
                        written = (dstRemaining - outbytesleft.value).toInt() / 2

                        read
                    }

                    bytesConsumed += read

                    if (read == 0) {
                        readSize++
                    } else {
                        readSize = 1
                        charsCopied += written
                    }

                    if (bytesConsumed < inputLength) readSize else 0
                }
            }
        }

        if (bytesConsumed < inputLength) {
            throw EOFException("Not enough bytes available: had only $bytesConsumed instead of $inputLength")
        }
        return chars.concatToString(0, 0 + charsCopied)
    } finally {
        iconv_close(cd)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
public actual fun CharsetEncoder.encodeUTF8(input: ByteReadPacket, dst: Output) {
    val cd = iconv_open(charset.name, "UTF-8")
    checkErrors(cd, "UTF-8")

    try {
        var readSize = 1
        var writeSize = 1

        while (true) {
            val srcView = input.prepareRead(readSize)
            if (srcView == null) {
                if (readSize != 1) throw MalformedInputException("...")
                break
            }

            dst.writeWhileSize(writeSize) { dstBuffer ->
                var written = 0

                dstBuffer.writeDirect { buffer ->
                    var read = 0

                    srcView.readDirect { src ->
                        memScoped {
                            val length = srcView.readRemaining.convert<size_t>()
                            val inbuf = alloc<CPointerVar<ByteVar>>()
                            val outbuf = alloc<CPointerVar<ByteVar>>()
                            val inbytesleft = alloc<size_tVar>()
                            val outbytesleft = alloc<size_tVar>()
                            val dstRemaining = dstBuffer.writeRemaining.convert<size_t>()

                            inbuf.value = src
                            outbuf.value = buffer
                            inbytesleft.value = length
                            outbytesleft.value = dstRemaining

                            val convResult = iconv(cd, inbuf.ptr, inbytesleft.ptr, outbuf.ptr, outbytesleft.ptr)
                                .toULong()

                            if (convResult == MAX_SIZE.toULong()) {
                                checkIconvResult(posix_errno())
                            }

                            read = (length - inbytesleft.value).toInt()
                            written = (dstRemaining - outbytesleft.value).toInt()
                        }

                        read
                    }

                    if (read == 0) {
                        readSize++
                        writeSize = 8
                    } else {
                        input.headPosition = srcView.readPosition
                        readSize = 1
                        writeSize = 1
                    }

                    if (written > 0 && srcView.canRead()) writeSize else 0
                }
                written
            }
        }
    } finally {
        iconv_close(cd)
    }
}

internal fun checkIconvResult(errno: Int) {
    if (errno == EILSEQ) throw MalformedInputException("Malformed or unmappable bytes at input")
    if (errno == EINVAL) return // too few input bytes
    if (errno == E2BIG) return // too few output buffer bytes

    throw IllegalStateException("Failed to call 'iconv' with error code $errno")
}
