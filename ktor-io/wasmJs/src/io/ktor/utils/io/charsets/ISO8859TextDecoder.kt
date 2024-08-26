/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import kotlinx.io.*

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
internal class ISO8859TextDecoder private constructor(
    val fatal: Boolean
) : Decoder {

    override fun decode(): String = ""

    override fun decode(buffer: ByteArray): String = buildPacket {
        val bytes = buffer
        for (element in bytes) {
            val point: Int = element.toCodePoint()

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

    override fun decodeStream(buffer: ByteArray): String =
        decode(buffer)

    companion object {
        fun tryCreate(encoding: String, fatal: Boolean = true): ISO8859TextDecoder? =
            when (encoding.trim().lowercase()) {
                in ENCODING_ALIASES -> ISO8859TextDecoder(fatal)
                else -> null
            }
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
