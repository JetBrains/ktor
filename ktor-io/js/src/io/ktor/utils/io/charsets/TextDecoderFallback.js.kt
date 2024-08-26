/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import kotlinx.io.*
import org.khronos.webgl.*

private val ENCODING_ALIASES = setOf(
    "ansi_x3.4-1968",
    "ascii",
    "cp1252",
    "cp819",
    "csisolatin1",
    "ibm819",
    "iso-8859-1",
    "iso-ir-100",
    "iso8859-1",
    "iso88591",
    "iso_8859-1",
    "iso_8859-1:1987",
    "l1",
    "latin1",
    "us-ascii",
    "windows-1252",
    "x-cp1252"
)

private val REPLACEMENT = byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte())

/**
 * Windows-1252 decoder.
 *
 * According to https://encoding.spec.whatwg.org/, ISO-8859-1 should be treated as windows-1252 for http.
 */
internal class TextDecoderFallback(
    encoding: String,
    val fatal: Boolean
) : Decoder {

    init {
        val requestedEncoding = encoding.trim().lowercase()
        check(ENCODING_ALIASES.contains(requestedEncoding)) { "$encoding is not supported." }
    }

    override fun decode(): String = ""

    override fun decode(buffer: ArrayBufferView): String = buildPacket {
        val bytes = buffer as Int8Array
        for (index in 0 until bytes.length) {
            val byte = bytes[index]
            val point: Int = byte.toCodePoint()

            if (point < 0) {
                check(!fatal) { "Invalid character: $point" }
                writeFully(REPLACEMENT)
                continue
            }

            if (point > 0xFF) {
                writeByte((point shr 8).toByte())
            }

            writeByte((point and 0xFF).toByte())
        }
    }.readByteArray().decodeToString()

    override fun decode(buffer: ArrayBufferView, options: dynamic): String {
        return decode(buffer)
    }
}

private fun Byte.toCodePoint(): Int {
    val value = toInt() and 0xFF
    if (value.isASCII()) {
        return value
    }

    return WIN1252_TABLE[value - 0x80]
}

private fun Int.isASCII(): Boolean = this in 0..0x7F
