/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.*
import kotlin.math.*

public actual object Charsets {
    public actual val UTF_8: Charset = CharsetDarwin("UTF-8")
    public actual val ISO_8859_1: Charset = CharsetDarwin("ISO-8859-1")
    internal val UTF_16: Charset = CharsetDarwin(platformUtf16)
}

internal actual fun findCharset(name: String): Charset {
    if (name == "UTF-8" || name == "utf-8" || name == "UTF8" || name == "utf8") return Charsets.UTF_8
    if (name == "ISO-8859-1" || name == "iso-8859-1" || name == "ISO_8859_1") return Charsets.ISO_8859_1
    if (name == "UTF-16" || name == "utf-16" || name == "UTF16" || name == "utf16") return Charsets.UTF_16

    return CharsetDarwin(name)
}

private class CharsetDarwin(name: String) : Charset(name) {
    @OptIn(UnsafeNumber::class)
    val encoding: NSStringEncoding = when (name.uppercase()) {
        "UTF-8" -> NSUTF8StringEncoding
        "ISO-8859-1" -> NSISOLatin1StringEncoding
        "UTF-16" -> NSUTF16StringEncoding
        "UTF-16BE" -> NSUTF16BigEndianStringEncoding
        "UTF-16LE" -> NSUTF16LittleEndianStringEncoding
        "UTF-32" -> NSUTF32StringEncoding
        "UTF-32BE" -> NSUTF32BigEndianStringEncoding
        "UTF-32LE" -> NSUTF32LittleEndianStringEncoding
        "ASCII" -> NSASCIIStringEncoding
        "NEXTSTEP" -> NSNEXTSTEPStringEncoding
        "JAPANESE_EUC" -> NSJapaneseEUCStringEncoding
        "LATIN1" -> NSISOLatin1StringEncoding
        else -> throw IllegalArgumentException("Charset $name is not supported by darwin.")
    }

    override fun newEncoder(): CharsetEncoder = object : CharsetEncoder(this) {
    }

    override fun newDecoder(): CharsetDecoder = object : CharsetDecoder(this) {
    }
}

@Suppress("DEPRECATION")
@OptIn(UnsafeNumber::class)
internal actual fun CharsetEncoder.encodeImpl(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Buffer): Int {
    val charset = _charset as? CharsetDarwin ?: error("Charset $this is not supported by darwin.")

    val max = dst.writeRemaining
    val endIndex = min(toIndex, fromIndex + max)

    @Suppress("CAST_NEVER_SUCCEEDS")
    val content = input.substring(fromIndex, endIndex) as? NSString ?: error("Failed to convert input to NSString.")

    val data = content.dataUsingEncoding(charset.encoding)
        ?.toByteArray()
        ?: throw MalformedInputException("Failed to convert String to Bytes using $charset")

    dst.writeFully(data)
    return data.size
}

@Suppress("CAST_NEVER_SUCCEEDS", "DEPRECATION")
@OptIn(UnsafeNumber::class, BetaInteropApi::class)
public actual fun CharsetDecoder.decode(input: Input, dst: Appendable, max: Int): Int {
    if (max != Int.MAX_VALUE) {
        throw IOException("Max argument is deprecated")
    }

    val charset = _charset as? CharsetDarwin ?: error("Charset $this is not supported by darwin.")
    val source: ByteArray = input.readBytes()

    val data = source.toNSData()
    val content = NSString.create(data, charset.encoding) as? String
        ?: throw MalformedInputException("Failed to convert Bytes to String using $charset")

    dst.append(content)
    return content.length
}

@OptIn(BetaInteropApi::class, UnsafeNumber::class)
@Suppress("DEPRECATION")
public actual fun CharsetEncoder.encodeUTF8(input: ByteReadPacket, dst: Output) {
    val charset = _charset as? CharsetDarwin ?: error("Charset $this is not supported by darwin.")
    val source: ByteArray = input.readBytes()

    val data = source.toNSData()

    @Suppress("CAST_NEVER_SUCCEEDS")
    val content = NSString.create(data, charset.encoding) as? String
        ?: throw MalformedInputException("Failed to convert Bytes to String using $charset")

    dst.writeFully(content.toByteArray())
}

@Suppress("DEPRECATION")
@OptIn(UnsafeNumber::class, BetaInteropApi::class)
public actual fun CharsetDecoder.decodeExactBytes(input: Input, inputLength: Int): String {
    val charset = _charset as? CharsetDarwin ?: error("Charset $this is not supported by darwin.")
    val source = input.readBytes(inputLength)

    val data = source.toNSData()

    @Suppress("CAST_NEVER_SUCCEEDS")
    val content = NSString.create(data, charset.encoding) as? String
        ?: throw MalformedInputException("Failed to convert Bytes to String using $charset")

    return content
}

@Suppress("DEPRECATION")
@OptIn(UnsafeNumber::class, BetaInteropApi::class)
internal actual fun CharsetDecoder.decodeBuffer(
    input: Buffer,
    out: Appendable,
    lastBuffer: Boolean,
    max: Int
): Int {
    val charset = _charset as? CharsetDarwin ?: error("Charset $this is not supported by darwin.")

    val count = input.readBytes(min(input.readRemaining, max))
    val data = count.toNSData()

    @Suppress("CAST_NEVER_SUCCEEDS")
    val content = NSString.create(data, charset.encoding) as? String
        ?: throw MalformedInputException("Failed to convert Bytes to String using $charset")

    out.append(content)
    return content.length
}

@OptIn(UnsafeNumber::class)
internal actual fun CharsetEncoder.encodeToByteArrayImpl(
    input: CharSequence,
    fromIndex: Int,
    toIndex: Int
): ByteArray {
    val charset = _charset as? CharsetDarwin ?: error("Charset $this is not supported by darwin.")

    @Suppress("CAST_NEVER_SUCCEEDS")
    val content = input.substring(fromIndex, toIndex) as? NSString ?: error("Failed to convert input to NSString.")

    return content.dataUsingEncoding(charset.encoding)
        ?.toByteArray()
        ?: throw MalformedInputException("Failed to convert String to Bytes using $charset")
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (isEmpty()) return@apply
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.convert())
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isEmpty()) return result

    result.usePinned {
        memcpy(it.addressOf(0), bytes, length)
    }

    return result
}
