/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.partialcontent

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlin.coroutines.*
import kotlin.random.*

// RFC7233 sec 3.2
internal suspend fun checkIfRangeHeader(
    content: OutgoingContent.ReadChannelContent,
    call: ApplicationCall
): Boolean {
    val conditionalHeadersPlugin = call.application.pluginOrNull(ConditionalHeaders)
    val ifRange = try {
        call.request.headers.getAll(HttpHeaders.IfRange)
            ?.map { parseIfRangeHeader(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.reduce { acc, list -> acc + list }
            ?.parseVersions()
            ?: return true
    } catch (_: Throwable) {
        return false
    }

    val versions = if (conditionalHeadersPlugin != null) {
        call.versionsFor(content)
    } else {
        content.headers.parseVersions().takeIf { it.isNotEmpty() } ?: call.response.headers.allValues().parseVersions()
    }

    return versions.all { version ->
        when (version) {
            is LastModifiedVersion -> checkLastModified(version, ifRange)
            is EntityTagVersion -> checkEntityTags(version, ifRange)
            else -> true
        }
    }
}

internal fun checkLastModified(actual: LastModifiedVersion, ifRange: List<Version>): Boolean {
    val actualDate = actual.lastModified.truncateToSeconds()

    return ifRange.all { condition ->
        when (condition) {
            is LastModifiedVersion -> actualDate <= condition.lastModified
            else -> true
        }
    }
}

internal fun checkEntityTags(actual: EntityTagVersion, ifRange: List<Version>): Boolean {
    return ifRange.all { condition ->
        when (condition) {
            is EntityTagVersion -> actual.etag == condition.etag
            else -> true
        }
    }
}

internal suspend fun BodyTransformedHook.Context.processRange(
    content: OutgoingContent.ReadChannelContent,
    rangesSpecifier: RangesSpecifier,
    length: Long,
    maxRangeCount: Int
) {
    require(length >= 0L)
    val merged = rangesSpecifier.merge(length, maxRangeCount)
    if (merged.isEmpty()) {
        LOGGER.trace("Responding 416 RequestedRangeNotSatisfiable for ${call.request.uri}: range is empty")
        call.response.contentRange(
            range = null,
            fullLength = length
        ) // https://tools.ietf.org/html/rfc7233#section-4.4
        val statusCode = HttpStatusCode.RequestedRangeNotSatisfiable.description(
            "Couldn't satisfy range request $rangesSpecifier: " +
                "it should comply with the restriction [0; $length)"
        )
        transformBodyTo(HttpStatusCodeContent(statusCode))
        return
    }

    when {
        merged.size != 1 && !merged.isAscending() -> {
            // merge into single range for non-seekable channel
            val resultRange = rangesSpecifier.mergeToSingle(length)!!
            processSingleRange(content, resultRange, length)
        }

        merged.size == 1 -> processSingleRange(content, merged.single(), length)
        else -> processMultiRange(content, merged, length)
    }
}

internal fun BodyTransformedHook.Context.processSingleRange(
    content: OutgoingContent.ReadChannelContent,
    range: LongRange,
    length: Long
) {
    LOGGER.trace("Responding 206 PartialContent for ${call.request.uri}: single range $range")
    transformBodyTo(PartialOutgoingContent.Single(call.isGet(), content, range, length))
}

internal suspend fun BodyTransformedHook.Context.processMultiRange(
    content: OutgoingContent.ReadChannelContent,
    ranges: List<LongRange>,
    length: Long
) {
    val boundary = "ktor-boundary-" + hex(Random.nextBytes(16))

    call.suppressCompression() // multirange with compression is not supported yet (KTOR-5794)

    LOGGER.trace(
        "Responding 206 PartialContent for ${call.request.uri}: multiple range ${ranges.joinToString(",")}"
    )
    transformBodyTo(PartialOutgoingContent.Multiple(coroutineContext, call.isGet(), content, ranges, length, boundary))
}

internal fun ApplicationCall.isGet() = request.local.method == HttpMethod.Get

internal fun ApplicationCall.isGetOrHead() = isGet() || request.local.method == HttpMethod.Head

internal fun List<LongRange>.isAscending(): Boolean =
    fold(true to 0L) { acc, e -> (acc.first && acc.second <= e.first) to e.first }.first

internal fun parseIfRangeHeader(header: String): List<HeaderValue> {
    if (header.endsWith(" GMT")) {
        return listOf(HeaderValue(header))
    }

    return parseHeaderValue(header)
}

internal fun List<HeaderValue>.parseVersions(): List<Version> = mapNotNull { field ->
    check(field.quality == 1.0) { "If-Range doesn't support quality" }
    check(field.params.isEmpty()) { "If-Range doesn't support parameters" }

    parseVersion(field.value)
}

internal fun parseVersion(value: String): Version? {
    if (value.isBlank()) return null
    check(!value.startsWith("W/"))

    if (value.startsWith("\"")) {
        return EntityTagVersion.parseSingle(value)
    }

    return LastModifiedVersion(value.fromHttpToGmtDate())
}
