/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*

/**
 * Specifies a key for the VersionList extension property for [OutgoingContent].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.VersionListProperty)
 */
public val VersionListProperty: AttributeKey<List<Version>> = AttributeKey("VersionList")

/**
 * Gets or sets a list of [Version] instances as an extension property on this content.
 */
public var OutgoingContent.versions: List<Version>
    get() = getProperty(VersionListProperty) ?: emptyList()
    set(value) = setProperty(VersionListProperty, value)

/**
 * A content version.
 *
 * An example of version is [EntityTagVersion] or [LastModifiedVersion].
 */
public interface Version {
    /**
     * Checks [requestHeaders] against this version and returns [VersionCheckResult].
     */
    public fun check(requestHeaders: Headers): VersionCheckResult

    /**
     * Appends relevant headers to the builder.
     */
    public fun appendHeadersTo(builder: HeadersBuilder)
}

/**
 * Represent the result of the version comparison between content being sent and HTTP request.
 *
 * @param statusCode represents [HttpStatusCode] associated with the result.
 */
public enum class VersionCheckResult(public val statusCode: HttpStatusCode) {
    /**
     * Indicates that content needs to be sent to recipient.
     */
    OK(HttpStatusCode.OK),

    /**
     * Indicates that content has not been modified according to headers sent by the client.
     */
    NOT_MODIFIED(HttpStatusCode.NotModified),

    /**
     * One or more conditions given in the request header fields evaluated to false.
     */
    PRECONDITION_FAILED(HttpStatusCode.PreconditionFailed)
}

/**
 * Creates an instance of [LastModifiedVersion] that passes the given [lastModified] date through the
 * `If-Modified-Since` and `If-Unmodified-Since` conditional headers provided by the client.
 *
 * For better accuracy, use ETag instead.
 *
 *  @param lastModified of the current content, for example the file's last modified date
 */
public data class LastModifiedVersion(val lastModified: GMTDate) : Version {
    private val truncatedModificationDate: GMTDate = lastModified.truncateToSeconds()

    /**
     *  @return [VersionCheckResult.OK] if all headers pass or there are no headers in the request,
     *  [VersionCheckResult.NOT_MODIFIED] for `If-Modified-Since`,
     *  [VersionCheckResult.PRECONDITION_FAILED] for `If-Unmodified-Since`
     */
    override fun check(requestHeaders: Headers): VersionCheckResult {
        val modifiedSince = requestHeaders.getAll(HttpHeaders.IfModifiedSince)?.parseDates()
        if (modifiedSince != null && !ifModifiedSince(modifiedSince)) {
            return VersionCheckResult.NOT_MODIFIED
        }

        val unmodifiedSince = requestHeaders.getAll(HttpHeaders.IfUnmodifiedSince)?.parseDates()
        if (unmodifiedSince != null && !ifUnmodifiedSince(unmodifiedSince)) {
            return VersionCheckResult.PRECONDITION_FAILED
        }

        return VersionCheckResult.OK
    }

    /**
     * If-Modified-Since logic: all [dates] should be _before_ this date (truncated to seconds).
     */
    public fun ifModifiedSince(dates: List<GMTDate>): Boolean {
        return dates.any { truncatedModificationDate > it }
    }

    /**
     * If-Unmodified-Since logic: all [dates] should not be before this date (truncated to seconds).
     */
    public fun ifUnmodifiedSince(dates: List<GMTDate>): Boolean {
        return dates.all { truncatedModificationDate <= it }
    }

    override fun appendHeadersTo(builder: HeadersBuilder) {
        builder[HttpHeaders.LastModified] = lastModified.toHttpDate()
    }

    private fun List<String>.parseDates(): List<GMTDate>? =
        filter { it.isNotBlank() }
            .mapNotNull {
                try {
                    it.fromHttpToGmtDate()
                } catch (_: Throwable) {
                    // according to RFC7232 sec 3.3 illegal dates should be ignored
                    null
                }
            }
            .takeIf { it.isNotEmpty() }
}

/**
 * Creates an instance of [EntityTagVersion] parsing the [spec] via [EntityTagVersion.parseSingle].
 */
@Suppress("FunctionName", "CONFLICTING_OVERLOADS")
public fun EntityTagVersion(spec: String): EntityTagVersion {
    return EntityTagVersion.parseSingle(spec)
}

/**
 * This version checks the [etag] value and pass it through conditions supplied by the remote client.
 * Depending on the conditions, it produces the return value of enum type [VersionCheckResult].
 *
 * It never handles `If-None-Match: *`  as it is related to non-etag logic (for example, Last modified checks).
 *
 * @param etag - entity tag, for example file's content hash
 * @param weak - whether strong or weak validation should be applied
 * @return [VersionCheckResult.OK] if all headers pass or there was no related headers,
 * [VersionCheckResult.NOT_MODIFIED] for successful If-None-Match,
 * [VersionCheckResult.PRECONDITION_FAILED] for failed If-Match
 */
public data class EntityTagVersion(val etag: String, val weak: Boolean) : Version {
    private val normalized: String = when {
        etag == "*" -> etag
        etag.startsWith("\"") -> etag
        else -> etag.quote()
    }

    init {
        for (index in etag.indices) {
            val ch = etag[index]
            if (ch <= ' ' || ch == '\"') {
                require(index == 0 || index == etag.lastIndex) { "Character '$ch' is not allowed in entity-tag." }
            }
        }
    }

    override fun check(requestHeaders: Headers): VersionCheckResult {
        requestHeaders[HttpHeaders.IfNoneMatch]?.let { parse(it) }?.let { givenNoneMatchEtags ->
            noneMatch(givenNoneMatchEtags).let { result ->
                if (result != VersionCheckResult.OK) return result
            }
        }

        requestHeaders[HttpHeaders.IfMatch]?.let { parse(it) }?.let { givenMatchEtags ->
            match(givenMatchEtags).let { result ->
                if (result != VersionCheckResult.OK) return result
            }
        }

        return VersionCheckResult.OK
    }

    /**
     * Checks whether two entity-tags match (strong).
     */
    public fun match(other: EntityTagVersion): Boolean {
        if (this == STAR || other == STAR) return true
        return normalized == other.normalized
    }

    /**
     * Specifies `If-None-Match` logic using the [match] function.
     */
    public fun noneMatch(givenNoneMatchEtags: List<EntityTagVersion>): VersionCheckResult {
        if (STAR in givenNoneMatchEtags) return VersionCheckResult.OK

        if (givenNoneMatchEtags.any { match(it) }) {
            return VersionCheckResult.NOT_MODIFIED
        }

        return VersionCheckResult.OK
    }

    /**
     * Specifies `If-Match` logic using the [match] function.
     */
    public fun match(givenMatchEtags: List<EntityTagVersion>): VersionCheckResult {
        if (givenMatchEtags.isEmpty()) return VersionCheckResult.OK
        if (STAR in givenMatchEtags) return VersionCheckResult.OK

        for (given in givenMatchEtags) {
            if (match(given)) {
                return VersionCheckResult.OK
            }
        }

        return VersionCheckResult.PRECONDITION_FAILED
    }

    override fun appendHeadersTo(builder: HeadersBuilder) {
        builder.etag(normalized)
    }

    public companion object {
        /**
         * Instance for `*` entity-tag pattern.
         */
        public val STAR: EntityTagVersion = EntityTagVersion("*", false)

        /**
         * Parses headers with a list of entity-tags. Useful for headers such as `If-Match`/`If-None-Match`.
         */
        public fun parse(headerValue: String): List<EntityTagVersion> {
            val rawEntries = parseHeaderValue(headerValue)
            return rawEntries.map { entry ->
                check(entry.quality == 1.0) { "entity-tag quality parameter is not allowed: ${entry.quality}." }
                check(entry.params.isEmpty()) { "entity-tag parameters are not allowed: ${entry.params}." }

                parseSingle(entry.value)
            }
        }

        /**
         * Parses a single entity-tag or pattern specification.
         */
        public fun parseSingle(value: String): EntityTagVersion {
            if (value == "*") return STAR

            val weak: Boolean
            val rawEtag: String

            if (value.startsWith("W/")) {
                weak = true
                rawEtag = value.drop(2)
            } else {
                weak = false
                rawEtag = value
            }

            val etag = when {
                rawEtag.startsWith("\"") -> rawEtag
                else -> rawEtag.quote()
            }

            return EntityTagVersion(etag, weak)
        }
    }
}
