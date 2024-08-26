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
 */
public class Url internal constructor(
    protocol: URLProtocol?,
    public val host: String,
    public val specifiedPort: Int,
    public val pathSegments: List<String>,
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
