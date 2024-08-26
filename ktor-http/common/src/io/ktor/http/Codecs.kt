/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.http

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.*

private val URL_ALPHABET = ((('a'..'z') + ('A'..'Z') + ('0'..'9')).map { it.code.toByte() }).toSet()
private val URL_ALPHABET_CHARS = ((('a'..'z') + ('A'..'Z') + ('0'..'9'))).toSet()
private val HEX_ALPHABET = (('a'..'f') + ('A'..'F') + ('0'..'9')).toSet()

/**
 * https://tools.ietf.org/html/rfc3986#section-2
 */
private val URL_PROTOCOL_PART = setOf(
    ':', '/', '?', '#', '[', ']', '@', // general
    '!', '$', '&', '\'', '(', ')', '*', ',', ';', '=', // sub-components
    '-', '.', '_', '~', '+' // unreserved
).map { it.code.toByte() }

/**
 * from `pchar` in https://tools.ietf.org/html/rfc3986#section-2
 */
private val VALID_PATH_PART = setOf(
    ':', '@',
    '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
    '-', '.', '_', '~'
)

/**
 * Characters allowed in attributes according: https://datatracker.ietf.org/doc/html/rfc5987
 * attr-char     = ALPHA / DIGIT / "!" / "#" / "$" / "&" / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
 */
internal val ATTRIBUTE_CHARACTERS: Set<Char> = URL_ALPHABET_CHARS + setOf(
    '!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~'
)

/**
 * Characters allowed in url according to https://tools.ietf.org/html/rfc3986#section-2.3
 */
private val SPECIAL_SYMBOLS = listOf('-', '.', '_', '~').map { it.code.toByte() }

/**
 * Encode url part as specified in
 * https://tools.ietf.org/html/rfc3986#section-2
 */
public fun String.encodeURLQueryComponent(
    encodeFull: Boolean = false,
    spaceToPlus: Boolean = false,
    charset: Charset = Charsets.UTF_8
): String = buildString {
    val content = charset.newEncoder().encode(this@encodeURLQueryComponent)
    content.forEach {
        when {
            it == ' '.code.toByte() -> if (spaceToPlus) append('+') else append("%20")
            it in URL_ALPHABET || (!encodeFull && it in URL_PROTOCOL_PART) -> append(it.toInt().toChar())
            else -> append(it.percentEncode())
        }
    }
}

/**
 * Encodes URL path segment. It escapes all illegal or ambiguous characters
 */
public fun String.encodeURLPathPart(): String = encodeURLPath(encodeSlash = true)

/**
 * Get the URL-encoding of this string, with options to skip / characters or to prevent
 * encoding already-encoded characters (%hh items).
 *
 * @see [RFC-3986](https://datatracker.ietf.org/doc/html/rfc3986#section-2.1)
 * @param encodeSlash / characters will be encoded as %2F; defaults to false
 * @param encodeEncoded %hh will be encoded as %25hh; defaults to true
 */
public fun String.encodeURLPath(
    encodeSlash: Boolean = false,
    encodeEncoded: Boolean = true,
): String = buildString {
    val charset = Charsets.UTF_8

    var index = 0
    while (index < this@encodeURLPath.length) {
        val current = this@encodeURLPath[index]
        if ((!encodeSlash && current == '/') || current in URL_ALPHABET_CHARS || current in VALID_PATH_PART) {
            append(current)
            index++
            continue
        }

        if (!encodeEncoded && current == '%' &&
            index + 2 < this@encodeURLPath.length &&
            this@encodeURLPath[index + 1] in HEX_ALPHABET &&
            this@encodeURLPath[index + 2] in HEX_ALPHABET
        ) {
            append(current)
            append(this@encodeURLPath[index + 1])
            append(this@encodeURLPath[index + 2])

            index += 3
            continue
        }

        val symbolSize = if (current.isSurrogate()) 2 else 1
        // we need to call newEncoder() for every symbol, otherwise it won't work
        charset.newEncoder().encode(this@encodeURLPath, index, index + symbolSize).forEach {
            append(it.percentEncode())
        }
        index += symbolSize
    }
}

/**
 * Encode [this] in percent encoding specified here:
 * https://tools.ietf.org/html/rfc5849#section-3.6
 */
public fun String.encodeOAuth(): String = encodeURLParameter()

/**
 * Encode [this] as query parameter key.
 */
public fun String.encodeURLParameter(
    spaceToPlus: Boolean = false
): String = buildString {
    val content = Charsets.UTF_8.newEncoder().encode(this@encodeURLParameter)
    content.forEach {
        when {
            it in URL_ALPHABET || it in SPECIAL_SYMBOLS -> append(it.toInt().toChar())
            spaceToPlus && it == ' '.code.toByte() -> append('+')
            else -> append(it.percentEncode())
        }
    }
}

internal fun String.percentEncode(allowedSet: Set<Char>): String {
    val encodedCount = count { it !in allowedSet }
    if (encodedCount == 0) return this

    val content = toByteArray(Charsets.UTF_8)

    val rawCount = length - encodedCount
    val resultSize = rawCount + (content.size - rawCount) * 3
    val result = CharArray(resultSize)

    var writeIndex = 0

    content.forEach {
        val char = it.toInt().toChar()

        if (char in allowedSet) {
            result[writeIndex++] = char
        } else {
            val code = it.toInt() and 0xff

            result[writeIndex++] = '%'
            result[writeIndex++] = hexDigitToChar(code shr 4)
            result[writeIndex++] = hexDigitToChar(code and 0xf)
        }
    }

    return result.concatToString()
}

/**
 * Encode [this] as query parameter value.
 */
internal fun String.encodeURLParameterValue(): String = encodeURLParameter(spaceToPlus = true)

/**
 * Decode URL query component
 */
public fun String.decodeURLQueryComponent(
    start: Int = 0,
    end: Int = length,
    plusIsSpace: Boolean = false,
    charset: Charset = Charsets.UTF_8
): String = decodeScan(start, end, plusIsSpace, charset)

/**
 * Decode percent encoded URL part within the specified range [[start], [end]).
 * This function is not intended to decode urlencoded forms so it doesn't decode plus character to space.
 */
public fun String.decodeURLPart(
    start: Int = 0,
    end: Int = length,
    charset: Charset = Charsets.UTF_8
): String = decodeScan(start, end, false, charset)

private fun String.decodeScan(start: Int, end: Int, plusIsSpace: Boolean, charset: Charset): String {
    for (index in start until end) {
        val ch = this[index]
        if (ch == '%' || (plusIsSpace && ch == '+')) {
            return decodeImpl(start, end, index, plusIsSpace, charset)
        }
    }
    return if (start == 0 && end == length) toString() else substring(start, end)
}

private fun CharSequence.decodeImpl(
    start: Int,
    end: Int,
    prefixEnd: Int,
    plusIsSpace: Boolean,
    charset: Charset
): String {
    val length = end - start
    // if length is big, it probably means it is encoded
    val sbSize = if (length > 255) length / 3 else length
    val sb = StringBuilder(sbSize)

    if (prefixEnd > start) {
        sb.append(this, start, prefixEnd)
    }

    var index = prefixEnd

    // reuse ByteArray for hex decoding stripes
    var bytes: ByteArray? = null

    while (index < end) {
        val c = this[index]
        when {
            plusIsSpace && c == '+' -> {
                sb.append(' ')
                index++
            }
            c == '%' -> {
                // if ByteArray was not needed before, create it with an estimate of remaining string be all hex
                if (bytes == null) {
                    bytes = ByteArray((end - index) / 3)
                }

                // fill ByteArray with all the bytes, so Charset can decode text
                var count = 0
                while (index < end && this[index] == '%') {
                    if (index + 2 >= end) {
                        throw URLDecodeException(
                            "Incomplete trailing HEX escape: ${substring(index)}, in $this at $index"
                        )
                    }

                    val digit1 = charToHexDigit(this[index + 1])
                    val digit2 = charToHexDigit(this[index + 2])
                    if (digit1 == -1 || digit2 == -1) {
                        throw URLDecodeException(
                            "Wrong HEX escape: %${this[index + 1]}${this[index + 2]}, in $this, at $index"
                        )
                    }

                    bytes[count++] = (digit1 * 16 + digit2).toByte()
                    index += 3
                }

                // Decode chars from bytes and put into StringBuilder
                // Note: Tried using ByteBuffer and using enc.decode() – it's slower
                sb.append(bytes.decodeToString(0, 0 + count))
            }
            else -> {
                sb.append(c)
                index++
            }
        }
    }

    return sb.toString()
}

/**
 * URL decoder exception
 */
public class URLDecodeException(message: String) : Exception(message)

private fun Byte.percentEncode(): String {
    val code = toInt() and 0xff
    val array = CharArray(3)
    array[0] = '%'
    array[1] = hexDigitToChar(code shr 4)
    array[2] = hexDigitToChar(code and 0xf)
    return array.concatToString()
}

private fun charToHexDigit(c2: Char) = when (c2) {
    in '0'..'9' -> c2 - '0'
    in 'A'..'F' -> c2 - 'A' + 10
    in 'a'..'f' -> c2 - 'a' + 10
    else -> -1
}

private fun hexDigitToChar(digit: Int): Char = when (digit) {
    in 0..9 -> '0' + digit
    else -> 'A' + digit - 10
}

private fun Source.forEach(block: (Byte) -> Unit) {
    takeWhile { buffer ->
        while (buffer.canRead()) {
            block(buffer.readByte())
        }
        true
    }
}
