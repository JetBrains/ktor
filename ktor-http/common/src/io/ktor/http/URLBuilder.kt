/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Select default port value from protocol.
 */
public const val DEFAULT_PORT: Int = 0

/**
 * A URL builder with all mutable components
 *
 * @property protocol URL protocol (scheme)
 * @property host name without port (domain)
 * @property port port number
 * @property user username part (optional)
 * @property password password part (optional)
 * @property pathSegments URL path without query
 * @property parameters URL query parameters
 * @property fragment URL fragment (anchor name)
 * @property trailingQuery keep a trailing question character even if there are no query parameters
 */
public class URLBuilder(
    protocol: URLProtocol? = null,
    public var host: String = "",
    port: Int = DEFAULT_PORT,
    user: String? = null,
    password: String? = null,
    pathSegments: List<String> = emptyList(),
    parameters: Parameters = Parameters.Empty,
    fragment: String = "",
    public var trailingQuery: Boolean = false
) {
    public var port: Int = port
        set(value) {
            require(value in 0..65535) {
                "Port must be between 0 and 65535, or $DEFAULT_PORT if not set. Provided: $value"
            }
            field = value
        }

    public var protocolOrNull: URLProtocol? = protocol
    public var protocol: URLProtocol
        get() = protocolOrNull ?: URLProtocol.HTTP
        set(value) { protocolOrNull = value }

    public var encodedUser: String? = user?.encodeURLParameter()

    public var user: String?
        get() = encodedUser?.decodeURLPart()
        set(value) {
            encodedUser = value?.encodeURLParameter()
        }

    public var encodedPassword: String? = password?.encodeURLParameter()
    public var password: String?
        get() = encodedPassword?.decodeURLPart()
        set(value) {
            encodedPassword = value?.encodeURLParameter()
        }

    public var encodedFragment: String = fragment.encodeURLQueryComponent()
    public var fragment: String
        get() = encodedFragment.decodeURLQueryComponent()
        set(value) {
            encodedFragment = value.encodeURLQueryComponent()
        }

    public var encodedPathSegments: List<String> = pathSegments.map { it.encodeURLPathPart() }

    public var pathSegments: List<String>
        get() = encodedPathSegments.map { it.decodeURLPart() }
        set(value) {
            encodedPathSegments = value.map { it.encodeURLPathPart() }
        }

    public var encodedParameters: ParametersBuilder = encodeParameters(parameters)
        set(value) {
            field = value
            parameters = UrlDecodedParametersBuilder(value)
        }

    public var parameters: ParametersBuilder = UrlDecodedParametersBuilder(encodedParameters)
        private set

    /**
     * Build a URL string
     */
    // note: 256 should fit 99.5% of all urls according to http://www.supermind.org/blog/740/average-length-of-a-url-part-2
    public fun buildString(): String {
        applyOrigin()
        return appendTo(StringBuilder(256)).toString()
    }

    override fun toString(): String {
        return appendTo(StringBuilder(256)).toString()
    }

    /**
     * Build a [Url] instance (everything is copied to a new instance)
     */
    public fun build(): Url {
        applyOrigin()
        return Url(
            protocol = protocolOrNull,
            host = host,
            specifiedPort = port,
            pathSegments = pathSegments,
            parameters = parameters.build(),
            fragment = fragment,
            user = user,
            password = password,
            trailingQuery = trailingQuery,
            urlString = buildString()
        )
    }

    private fun applyOrigin() {
        if (host.isNotEmpty() || protocol.name == "file") return
        host = originUrl.host
        if (protocolOrNull == null) protocolOrNull = originUrl.protocolOrNull
        if (port == DEFAULT_PORT) port = originUrl.specifiedPort
    }

    // Required to write external extension function
    public companion object {
        private val originUrl = Url(origin)
    }
}

private fun <A : Appendable> URLBuilder.appendTo(out: A): A {
    out.append(protocol.name)

    when (protocol.name) {
        "file" -> {
            out.appendFile(host, encodedPath)
            return out
        }

        "mailto" -> {
            out.appendMailto(encodedUserAndPassword, host)
            return out
        }
    }

    out.append("://")
    out.append(authority)

    out.appendUrlFullPath(encodedPath, encodedParameters, trailingQuery)

    if (encodedFragment.isNotEmpty()) {
        out.append('#')
        out.append(encodedFragment)
    }

    return out
}

private fun Appendable.appendMailto(encodedUser: String, host: String) {
    append(":")
    append(encodedUser)
    append(host)
}

private fun Appendable.appendFile(host: String, encodedPath: String) {
    append("://")
    append(host)
    if (!encodedPath.startsWith('/')) {
        append('/')
    }
    append(encodedPath)
}

/**
 * Hostname of current origin.
 *
 * It uses "http://localhost" for all platforms except js.
 */
public expect val URLBuilder.Companion.origin: String

/**
 * Create a copy of this builder. Modifications in a copy is not reflected in the original instance and vise-versa.
 */
public fun URLBuilder.clone(): URLBuilder = URLBuilder().takeFrom(this)

internal val URLBuilder.encodedUserAndPassword: String
    get() = buildString {
        appendUserAndPassword(encodedUser, encodedPassword)
    }

/**
 * Adds [segments] to current [encodedPath].
 *
 * @param segments path items to append
 * @param encodeSlash `true` to encode the '/' character to allow it to be a part of a path segment;
 * `false` to use '/' as a separator between path segments.
 */
public fun URLBuilder.appendPathSegments(segments: List<String>, encodeSlash: Boolean = false): URLBuilder {
    val pathSegments = if (!encodeSlash) segments.flatMap { it.split('/') } else segments
    val encodedSegments = pathSegments.map { it.encodeURLPathPart() }
    appendEncodedPathSegments(encodedSegments)

    return this
}

/**
 * Adds [components] to current [encodedPath]
 *
 * @param components path items to append
 * @param encodeSlash `true` to encode the '/' character to allow it to be a part of a path segment;
 * `false` to use '/' as a separator between path segments.
 */
public fun URLBuilder.appendPathSegments(vararg components: String, encodeSlash: Boolean = false): URLBuilder {
    return appendPathSegments(components.toList(), encodeSlash)
}

/**
 * Replace [components] in the current [encodedPath]. The [path] components will be escaped, except `/` character.
 * @param path path items to set
 */
public fun URLBuilder.path(vararg path: String) {
    encodedPathSegments = path.map { it.encodeURLPath() }
}

/**
 * Adds [segments] to current [encodedPath]
 */
public fun URLBuilder.appendEncodedPathSegments(segments: List<String>): URLBuilder {
    val endsWithSlash =
        encodedPathSegments.size > 1 && encodedPathSegments.last().isEmpty() && segments.isNotEmpty()
    val startWithSlash =
        segments.size > 1 && segments.first().isEmpty() && encodedPathSegments.isNotEmpty()
    encodedPathSegments = when {
        endsWithSlash && startWithSlash -> encodedPathSegments.dropLast(1) + segments.drop(1)
        endsWithSlash -> encodedPathSegments.dropLast(1) + segments
        startWithSlash -> encodedPathSegments + segments.drop(1)
        else -> encodedPathSegments + segments
    }
    return this
}

/**
 * Adds [components] to current [encodedPath]
 */
public fun URLBuilder.appendEncodedPathSegments(vararg components: String): URLBuilder =
    appendEncodedPathSegments(components.toList())

/**
 * [URLBuilder] authority.
 */
public val URLBuilder.authority: String
    get() = buildString {
        append(encodedUserAndPassword)
        append(host)

        if (port != DEFAULT_PORT && port != protocol.defaultPort) {
            append(":")
            append(port.toString())
        }
    }

public var URLBuilder.encodedPath: String
    get() = encodedPathSegments.joinPath()
    set(value) {
        encodedPathSegments = when {
            value.isBlank() -> emptyList()
            value == "/" -> ROOT_PATH
            else -> value.split('/').toMutableList()
        }
    }

private fun List<String>.joinPath(): String {
    if (isEmpty()) return ""
    if (size == 1) {
        if (first().isEmpty()) return "/"
        return first()
    }

    return joinToString("/")
}

/**
 * Sets the url parts using the specified [scheme], [host], [port] and [path].
 * Pass `null` to keep existing value in the [URLBuilder].
 */
public fun URLBuilder.set(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: URLBuilder.() -> Unit = {}
) {
    if (scheme != null) protocol = URLProtocol.createOrDefault(scheme)
    if (host != null) this.host = host
    if (port != null) this.port = port
    if (path != null) encodedPath = path
    block(this)
}

@Deprecated(
    message = "Please use appendPathSegments method",
    replaceWith = ReplaceWith("this.appendPathSegments(components"),
    level = DeprecationLevel.ERROR
)
public fun URLBuilder.pathComponents(vararg components: String): URLBuilder = appendPathSegments(components.toList())

@Deprecated(
    message = "Please use appendPathSegments method",
    replaceWith = ReplaceWith("this.appendPathSegments(components"),
    level = DeprecationLevel.ERROR
)
public fun URLBuilder.pathComponents(components: List<String>): URLBuilder = appendPathSegments(components)
