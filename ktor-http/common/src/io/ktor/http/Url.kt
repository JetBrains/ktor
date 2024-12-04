/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

/**
 * Represents an immutable URL
 *
 * @property protocol
 * @property host name without port (domain)
 * @property port the specified port or protocol default port
 * @property specifiedPort port number that was specified to override protocol's default
 * @property encodedPath encoded path without query string
 * @property parameters URL query parameters
 * @property fragment URL fragment (anchor name)
 * @property user username part of URL
 * @property password password part of URL
 * @property trailingQuery keep trailing question character even if there are no query parameters
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.Url)
 */
public class Url internal constructor(
    protocol: URLProtocol?,
    public val host: String,
    public val specifiedPort: Int,
    pathSegments: List<String>,
    public val parameters: Parameters,
    public val fragment: String,
    public val user: String?,
    public val password: String?,
    public val trailingQuery: Boolean,
    private val urlString: String
) {
    init {
        require(specifiedPort in 0..65535) {
            "Port must be between 0 and 65535, or $DEFAULT_PORT if not set. Provided: $specifiedPort"
        }
    }

    /**
     * A list containing the segments of the URL path.
     *
     * This property was designed to distinguish between absolute and relative paths,
     * so it will have an empty segment at the beginning for URLs with a hostname
     * and an empty segment at the end for URLs with a trailing slash.
     *
     * ```kotlin
     * val fullUrl = Url("http://ktor.io/docs/")
     * fullUrl.pathSegments == listOf("", "docs", "")
     *
     * val absolute = Url("/docs/")
     * absolute.pathSegments == listOf("", "docs", "")
     *
     * val relative = Url("docs")
     * relative.pathSegments == listOf("docs")
     * ```
     *
     * This behaviour may not be ideal if you're working only with full URLs.
     * If you don't require the specific handling of empty segments, consider using the [segments] property instead:
     *
     * ```kotlin
     * val fullUrl = Url("http://ktor.io/docs/")
     * fullUrl.segments == listOf("docs")
     *
     * val absolute = Url("/docs/")
     * absolute.segments == listOf("docs")
     *
     * val relative = Url("docs")
     * relative.segments == listOf("docs")
     * ```
     *
     * To address this issue, the current [pathSegments] property will be renamed to [rawSegments].
     */
    @Deprecated(
        """
        `pathSegments` is deprecated.

        This property will contain an empty path segment at the beginning for URLs with a hostname,
        and an empty path segment at the end for the URLs with a trailing slash. If you need to keep this behaviour please
        use [rawSegments]. If you only need to access the meaningful parts of the path, consider using [segments] instead.
             
        Please decide if you need [rawSegments] or [segments] explicitly.
        """,
        replaceWith = ReplaceWith("rawSegments")
    )
    @Suppress("CanBePrimaryConstructorProperty")
    public val pathSegments: List<String> = pathSegments

    /**
     * A list containing the segments of the URL path.
     *
     * This property is designed to distinguish between absolute and relative paths,
     * so it will have an empty segment at the beginning for URLs with a hostname
     * and an empty segment at the end for URLs with a trailing slash.
     *
     * ```kotlin
     * val fullUrl = Url("http://ktor.io/docs/")
     * fullUrl.rawSegments == listOf("", "docs", "")
     *
     * val absolute = Url("/docs/")
     * absolute.rawSegments == listOf("", "docs", "")
     *
     * val relative = Url("docs")
     * relative.rawSegments == listOf("docs")
     * ```
     *
     * This behaviour may not be ideal if you're working only with full URLs.
     * If you don't require the specific handling of empty segments, consider using the [segments] property instead:
     *
     * ```kotlin
     * val fullUrl = Url("http://ktor.io/docs/")
     * fullUrl.segments == listOf("docs")
     *
     * val absolute = Url("/docs/")
     * absolute.segments == listOf("docs")
     *
     * val relative = Url("docs")
     * relative.segments == listOf("docs")
     * ```
     */
    public val rawSegments: List<String> = pathSegments

    /**
     * A list of path segments derived from the URL, excluding any leading
     * and trailing empty segments.
     *
     * ```kotlin
     * val fullUrl = Url("http://ktor.io/docs/")
     * fullUrl.segments == listOf("docs")
     *
     * val absolute = Url("/docs/")
     * absolute.segments == listOf("docs")
     * val relative = Url("docs")
     * relative.segments == listOf("docs")
     * ```
     *
     * If you need to check for trailing slash and relative/absolute paths, please check the [rawSegments] property.
     **/
    public val segments: List<String> by lazy {
        if (pathSegments.isEmpty()) return@lazy emptyList()
        val start = if (pathSegments.first().isEmpty() && pathSegments.size > 1) 1 else 0
        val end = if (pathSegments.last().isEmpty()) pathSegments.lastIndex else pathSegments.lastIndex + 1
        pathSegments.subList(start, end)
    }

    public val protocolOrNull: URLProtocol? = protocol
    public val protocol: URLProtocol = protocolOrNull ?: URLProtocol.HTTP

    public val port: Int get() = specifiedPort.takeUnless { it == DEFAULT_PORT } ?: protocol.defaultPort

    public val encodedPath: String by lazy {
        if (pathSegments.isEmpty()) {
            return@lazy ""
        }
        val pathStartIndex = urlString.indexOf('/', this.protocol.name.length + 3)
        if (pathStartIndex == -1) {
            return@lazy ""
        }
        val pathEndIndex = urlString.indexOfAny(charArrayOf('?', '#'), pathStartIndex)
        if (pathEndIndex == -1) {
            return@lazy urlString.substring(pathStartIndex)
        }
        urlString.substring(pathStartIndex, pathEndIndex)
    }

    public val encodedQuery: String by lazy {
        val queryStart = urlString.indexOf('?') + 1
        if (queryStart == 0) return@lazy ""

        val queryEnd = urlString.indexOf('#', queryStart)
        if (queryEnd == -1) return@lazy urlString.substring(queryStart)

        urlString.substring(queryStart, queryEnd)
    }

    public val encodedPathAndQuery: String by lazy {
        val pathStart = urlString.indexOf('/', this.protocol.name.length + 3)
        if (pathStart == -1) {
            return@lazy ""
        }
        val queryEnd = urlString.indexOf('#', pathStart)
        if (queryEnd == -1) {
            return@lazy urlString.substring(pathStart)
        }
        urlString.substring(pathStart, queryEnd)
    }

    public val encodedUser: String? by lazy {
        if (user == null) return@lazy null
        if (user.isEmpty()) return@lazy ""
        val usernameStart = this.protocol.name.length + 3
        val usernameEnd = urlString.indexOfAny(charArrayOf(':', '@'), usernameStart)
        urlString.substring(usernameStart, usernameEnd)
    }

    public val encodedPassword: String? by lazy {
        if (password == null) return@lazy null
        if (password.isEmpty()) return@lazy ""
        val passwordStart = urlString.indexOf(':', this.protocol.name.length + 3) + 1
        val passwordEnd = urlString.indexOf('@')
        urlString.substring(passwordStart, passwordEnd)
    }

    public val encodedFragment: String by lazy {
        val fragmentStart = urlString.indexOf('#') + 1
        if (fragmentStart == 0) return@lazy ""

        urlString.substring(fragmentStart)
    }

    override fun toString(): String = urlString

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Url

        return urlString == other.urlString
    }

    override fun hashCode(): Int {
        return urlString.hashCode()
    }

    public companion object
}

/**
 * [Url] authority.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.authority)
 */
public val Url.authority: String
    get() = buildString {
        append(encodedUserAndPassword)
        append(hostWithPortIfSpecified)
    }

/**
 * A [Url] protocol and authority.
 */
public val Url.protocolWithAuthority: String
    get() = buildString {
        append(protocol.name)
        append("://")
        append(encodedUserAndPassword)

        if (specifiedPort == DEFAULT_PORT || specifiedPort == protocol.defaultPort) {
            append(host)
        } else {
            append(hostWithPort)
        }
    }

internal val Url.encodedUserAndPassword: String
    get() = buildString {
        appendUserAndPassword(encodedUser, encodedPassword)
    }
