/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.utils.io.*

/**
 * An HTTP parser exception
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.cio.ParserException)
 */
public class ParserException(message: String) : IllegalStateException(message)

private const val HTTP_LINE_LIMIT = 8192
private const val HTTP_STATUS_CODE_MIN_RANGE = 100
private const val HTTP_STATUS_CODE_MAX_RANGE = 999
private val hostForbiddenSymbols = setOf('/', '?', '#', '@')

/**
 * Parse an HTTP request line and headers
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.cio.parseRequest)
 */
public suspend fun parseRequest(input: ByteReadChannel): Request? {
    val builder = CharArrayBuilder()
    val range = MutableRange(0, 0)

    try {
        while (true) {
            if (!input.readUTF8LineTo(builder, HTTP_LINE_LIMIT)) return null
            range.end = builder.length
            if (range.start == range.end) continue

            val method = parseHttpMethod(builder, range)
            val uri = parseUri(builder, range)
            val version = parseVersion(builder, range)
            skipSpaces(builder, range)

            if (range.start != range.end) {
                throw ParserException("Extra characters in request line: ${builder.substring(range.start, range.end)}")
            }
            if (uri.isEmpty()) throw ParserException("URI is not specified")
            if (version.isEmpty()) throw ParserException("HTTP version is not specified")

            val headers = parseHeaders(input, builder, range) ?: return null

            return Request(method, uri, version, headers, builder)
        }
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

/**
 * Parse an HTTP response status line and headers
 */
public suspend fun parseResponse(input: ByteReadChannel): Response? {
    val builder = CharArrayBuilder()
    val range = MutableRange(0, 0)

    try {
        if (!input.readUTF8LineTo(builder, HTTP_LINE_LIMIT)) return null
        range.end = builder.length

        val version = parseVersion(builder, range)
        val statusCode = parseStatusCode(builder, range)
        skipSpaces(builder, range)
        val statusText = builder.subSequence(range.start, range.end)
        range.start = range.end

        val headers = parseHeaders(input, builder, range) ?: HttpHeadersMap(builder)

        return Response(version, statusCode, statusText, headers, builder)
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

/**
 * Parse http headers. Not applicable to request and response status lines.
 */
public suspend fun parseHeaders(input: ByteReadChannel): HttpHeadersMap {
    val builder = CharArrayBuilder()
    return parseHeaders(input, builder) ?: HttpHeadersMap(builder)
}

/**
 * Parse HTTP headers. Not applicable to request and response status lines.
 */
internal suspend fun parseHeaders(
    input: ByteReadChannel,
    builder: CharArrayBuilder,
    range: MutableRange = MutableRange(0, 0)
): HttpHeadersMap? {
    val headers = HttpHeadersMap(builder)

    try {
        while (true) {
            if (!input.readUTF8LineTo(builder, HTTP_LINE_LIMIT)) {
                headers.release()
                return null
            }

            range.end = builder.length
            val rangeLength = range.end - range.start

            if (rangeLength == 0) break
            if (rangeLength >= HTTP_LINE_LIMIT) error("Header line length limit exceeded")

            val nameStart = range.start
            val nameEnd = parseHeaderName(builder, range)

            val nameHash = builder.hashCodeLowerCase(nameStart, nameEnd)

            val headerEnd = range.end
            parseHeaderValue(builder, range)

            val valueStart = range.start
            val valueEnd = range.end
            val valueHash = builder.hashCodeLowerCase(valueStart, valueEnd)
            range.start = headerEnd

            headers.put(nameHash, valueHash, nameStart, nameEnd, valueStart, valueEnd)
        }

        val host = headers[HttpHeaders.Host]
        if (host != null) {
            validateHostHeader(host)
        }

        return headers
    } catch (t: Throwable) {
        headers.release()
        throw t
    }
}

private fun validateHostHeader(host: CharSequence) {
    if (host.endsWith(":")) {
        throw ParserException("Host header with ':' should contains port: $host")
    }

    if (host.any { hostForbiddenSymbols.contains(it) }) {
        throw ParserException("Host cannot contain any of the following symbols: $hostForbiddenSymbols")
    }
}

private fun parseHttpMethod(text: CharSequence, range: MutableRange): HttpMethod {
    skipSpaces(text, range)
    val exact = DefaultHttpMethods.search(text, range.start, range.end) { ch, _ -> ch == ' ' }.singleOrNull()
    if (exact != null) {
        range.start += exact.value.length
        return exact
    }

    return parseHttpMethodFull(text, range)
}

private fun parseHttpMethodFull(text: CharSequence, range: MutableRange): HttpMethod {
    return HttpMethod(nextToken(text, range).toString())
}

private fun parseUri(text: CharSequence, range: MutableRange): CharSequence {
    skipSpaces(text, range)
    val start = range.start
    val spaceOrEnd = findSpaceOrEnd(text, range)
    val length = spaceOrEnd - start

    if (length <= 0) return ""
    if (length == 1 && text[start] == '/') {
        range.start = spaceOrEnd
        return "/"
    }

    val s = text.subSequence(start, spaceOrEnd)
    range.start = spaceOrEnd
    return s
}

private val versions = AsciiCharTree.build(listOf("HTTP/1.0", "HTTP/1.1"))

private fun parseVersion(text: CharSequence, range: MutableRange): CharSequence {
    skipSpaces(text, range)

    check(range.start < range.end) { "Failed to parse version: $text" }
    val exact = versions.search(text, range.start, range.end) { ch, _ -> ch == ' ' }.singleOrNull()
    if (exact != null) {
        range.start += exact.length
        return exact
    }

    unsupportedHttpVersion(nextToken(text, range))
}

private fun parseStatusCode(text: CharSequence, range: MutableRange): Int {
    skipSpaces(text, range)
    var status = 0
    var newStart = range.end

    for (idx in range.start until range.end) {
        val ch = text[idx]
        if (ch == ' ') {
            if (statusOutOfRange(status)) {
                throw ParserException("Status-code must be 3-digit. Status received: $status.")
            }
            newStart = idx
            break
        } else if (ch in '0'..'9') {
            status = status * 10 + (ch - '0')
        } else {
            val code = text.substring(range.start, findSpaceOrEnd(text, range))
            throw NumberFormatException("Illegal digit $ch in status code $code")
        }
    }

    range.start = newStart
    return status
}

private fun statusOutOfRange(code: Int) = code < HTTP_STATUS_CODE_MIN_RANGE || code > HTTP_STATUS_CODE_MAX_RANGE

/**
 * Returns index of the next character after the last header name character,
 * range.start is modified to point to the next character after colon.
 */
internal fun parseHeaderName(text: CharArrayBuilder, range: MutableRange): Int {
    var index = range.start
    val end = range.end

    while (index < end) {
        val ch = text[index]
        if (ch == ':' && index != range.start) {
            range.start = index + 1
            return index
        }

        if (isDelimiter(ch)) {
            parseHeaderNameFailed(text, index, range.start, ch)
        }

        index++
    }

    noColonFound(text, range)
}

private fun parseHeaderNameFailed(text: CharArrayBuilder, index: Int, start: Int, ch: Char): Nothing {
    if (ch == ':') {
        throw ParserException("Empty header names are not allowed as per RFC7230.")
    }
    if (index == start) {
        throw ParserException(
            "Multiline headers via line folding is not supported " +
                "since it is deprecated as per RFC7230."
        )
    }
    characterIsNotAllowed(text, ch)
}

internal fun parseHeaderValue(text: CharArrayBuilder, range: MutableRange) {
    val start = range.start
    val end = range.end
    var index = start

    index = skipSpacesAndHorizontalTabs(text, index, end)

    if (index >= end) {
        range.start = end
        return
    }

    val valueStart = index
    var valueLastIndex = index

    while (index < end) {
        when (val ch = text[index]) {
            HTAB, ' ' -> {
            }
            '\r', '\n' -> characterIsNotAllowed(text, ch)
            else -> valueLastIndex = index
        }

        index++
    }

    range.start = valueStart
    range.end = valueLastIndex + 1
}

private fun noColonFound(text: CharSequence, range: MutableRange): Nothing {
    throw ParserException("No colon in HTTP header in ${text.substring(range.start, range.end)} in builder: \n$text")
}

private fun characterIsNotAllowed(text: CharSequence, ch: Char): Nothing =
    throw ParserException("Character with code ${(ch.code and 0xff)} is not allowed in header names, \n$text")

private fun isDelimiter(ch: Char): Boolean {
    return ch <= ' ' || ch in "\"(),/:;<=>?@[\\]{}"
}

private fun unsupportedHttpVersion(result: CharSequence): Nothing {
    throw ParserException("Unsupported HTTP version: $result")
}
