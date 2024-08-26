/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.io.*
import kotlinx.io.unsafe.*
import platform.iconv.*
import platform.posix.*

internal val MAX_SIZE: size_t = size_t.MAX_VALUE
private const val DECODING_BUFFER_SIZE = 8192

public actual object Charsets {
    public actual val UTF_8: Charset by lazy { CharsetIconv("UTF-8") }
    public actual val ISO_8859_1: Charset by lazy { CharsetIconv("ISO-8859-1") }
    internal val UTF_16: Charset by lazy { CharsetIconv(platformUtf16) }
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

internal actual fun findCharset(name: String): Charset {
    if (name == "UTF-8" || name == "utf-8" || name == "UTF8" || name == "utf8") return Charsets.UTF_8
    if (name == "ISO-8859-1" || name == "iso-8859-1" || name == "ISO_8859_1") return Charsets.ISO_8859_1
    if (name == "UTF-16" || name == "utf-16" || name == "UTF16" || name == "utf16") return Charsets.UTF_16

    return CharsetIconv(name)
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

@OptIn(ExperimentalForeignApi::class, InternalIoApi::class, UnsafeIoApi::class)
internal actual fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Sink): Int {
    val length = toIndex - fromIndex
    if (length == 0) return 0

    val chars = input.substring(fromIndex, toIndex).toCharArray()
    val charset = iconvCharsetName(_charset._name)
    val cd: COpaquePointer? = iconv_open(charset, platformUtf16)
    checkErrors(cd, charset)

    try {
        memScoped {
            chars.usePinned { from ->
                val inbuf = alloc<CPointerVar<ByteVar>>()
                val outbuf = alloc<CPointerVar<ByteVar>>()
                val inbytesleft = alloc<size_tVar>()
                inbuf.value = from.addressOf(0).reinterpret()
                inbytesleft.value = (length * 2).toULong()
                val outbytesleft = alloc<size_tVar>()

                while (inbytesleft.value.toLong() > 0) {
                    UnsafeBufferOperations.writeToTail(dst.buffer, 1) { to, toStart, toEnd ->
                        to.usePinned {
                            outbuf.value = it.addressOf(toStart).reinterpret()
                            outbytesleft.value = (toEnd - toStart).toULong()

                            /**
                             * inbuf is shifted by the number of bytes consumed
                             * inbytesleft is decremented by the number of bytes consumed
                             * outbuf is shifted by the number of bytes written
                             * outbytesleft is decremented by the number of bytes written
                             */
                            val convertResult =
                                iconv(cd, inbuf.ptr, inbytesleft.ptr, outbuf.ptr, outbytesleft.ptr).toULong()
                            if (convertResult == MAX_SIZE.toULong()) {
                                checkIconvResult(posix_errno())
                            }

                            val consumed = (toEnd - toStart - outbytesleft.value.toInt())
                            consumed
                        }
                    }
                }
            }
        }
    } finally {
        iconv_close(cd)
    }

    return length
}

@OptIn(ExperimentalForeignApi::class, UnsafeIoApi::class, InternalIoApi::class)
public actual fun CharsetDecoder.decode(input: Source, dst: Appendable, max: Int): Int {
    val charset = iconvCharsetName(charset.name)
    val cd = iconv_open(platformUtf16, charset)
    checkErrors(cd, charset)
    val chars = CharArray(DECODING_BUFFER_SIZE)
    var copied = 0
    try {
        chars.usePinned { output ->
            memScoped {
                val inbuf = alloc<CPointerVar<ByteVar>>()
                val outbuf = alloc<CPointerVar<ByteVar>>()
                val inbytesleft = alloc<size_tVar>()
                val outbytesleft = alloc<size_tVar>()

                while (!input.exhausted() && copied < max) {
                    UnsafeBufferOperations.readFromHead(input.buffer) { data, startIndex, endIndex ->
                        data.usePinned {
                            inbuf.value = it.addressOf(startIndex).reinterpret()
                            inbytesleft.value = (endIndex - startIndex).toULong()

                            outbuf.value = output.addressOf(0).reinterpret()
                            outbytesleft.value = (chars.size * 2).toULong()

                            val result = iconv(cd, inbuf.ptr, inbytesleft.ptr, outbuf.ptr, outbytesleft.ptr)
                            if (result == MAX_SIZE.toULong()) {
                                checkIconvResult(posix_errno())
                            }

                            val written = (chars.size * 2 - outbytesleft.value.toInt()) / 2
                            repeat(written) {
                                dst.append(chars[it])
                            }

                            val consumed = (endIndex - startIndex - inbytesleft.value.toInt())
                            copied += consumed
                            consumed
                        }
                    }
                }
            }
        }

        return copied
    } finally {
        iconv_close(cd)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray = buildPacket {
    encodeToImpl(this, input, fromIndex, toIndex)
}.readByteArray()

internal fun checkIconvResult(errno: Int) {
    if (errno == EILSEQ) throw MalformedInputException("Malformed or unmappable bytes at input")
    if (errno == EINVAL) return // too few input bytes
    if (errno == E2BIG) return // too few output buffer bytes

    throw IllegalStateException("Failed to call 'iconv' with error code $errno")
}
