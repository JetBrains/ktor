/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.partialcontent

import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

private val NEWLINE = "\r\n".toByteArray(Charsets.ISO_8859_1)
private val FIXED_HEADERS_PART_LENGTH = 14 + HttpHeaders.ContentType.length + HttpHeaders.ContentRange.length

/**
 * Start multirange response writer coroutine
 */

internal actual fun CoroutineScope.writeMultipleRangesImpl(
    channelProducer: (LongRange) -> ByteReadChannel,
    ranges: List<LongRange>,
    fullLength: Long?,
    boundary: String,
    contentType: String
): ByteReadChannel = writer(Dispatchers.Unconfined, autoFlush = true) {
    for (range in ranges) {
        val current = channelProducer(range)
        channel.writeHeaders(range, boundary, contentType, fullLength)
        current.copyTo(channel)
        channel.writeFully(NEWLINE)
    }

    channel.writeFully("--$boundary--".toByteArray(Charsets.ISO_8859_1))
    channel.writeFully(NEWLINE)
}.channel

private suspend fun ByteWriteChannel.writeHeaders(
    range: LongRange,
    boundary: String,
    contentType: String,
    fullLength: Long?
) {
    val contentRangeHeaderValue = contentRangeHeaderValue(range, fullLength, RangeUnits.Bytes)
    val estimate = boundary.length + contentType.length + contentRangeHeaderValue.length + FIXED_HEADERS_PART_LENGTH
    val headers = buildString(estimate) {
        append("--")
        append(boundary)
        append("\r\n")

        append(HttpHeaders.ContentType)
        append(": ")
        append(contentType)
        append("\r\n")

        append(HttpHeaders.ContentRange)
        append(": ")
        append(contentRangeHeaderValue)
        append("\r\n")

        append("\r\n")
    }.toByteArray(Charsets.ISO_8859_1)

    writeFully(headers)
}

internal actual fun calculateMultipleRangesBodyLength(
    ranges: List<LongRange>,
    fullLength: Long?,
    boundary: String,
    contentType: String
): Long {
    // header length + range size + newline
    val contentLength = ranges.sumOf {
        calculateHeadersLength(
            it,
            boundary,
            contentType,
            fullLength
        ) + it.last - it.first + 3L
    }
    // -- + boundary + -- + newline
    return contentLength + boundary.length + 6
}

private fun calculateHeadersLength(
    range: LongRange,
    boundary: String,
    contentType: String,
    fullLength: Long?
): Int {
    val contentRangeHeaderValue = contentRangeHeaderValue(range, fullLength, RangeUnits.Bytes)
    return boundary.length + contentType.length + contentRangeHeaderValue.length + FIXED_HEADERS_PART_LENGTH
}
