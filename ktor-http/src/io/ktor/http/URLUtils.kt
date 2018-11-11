package io.ktor.http

import io.ktor.util.*

/**
 * Construct [Url] from [urlString]
 */
fun Url(urlString: String): Url = URLBuilder(urlString).build()

/**
 * Construct [UrlBuilder] from [urlString]
 */
fun URLBuilder(urlString: String): URLBuilder = URLBuilder().takeFrom(urlString)

/**
 * Take url parts from [urlString]
 */
fun URLBuilder.takeFrom(urlString: String): URLBuilder {
    val parts = urlString.urlParts()

    parts["protocol"]?.let {
        protocol = URLProtocol.createOrDefault(it)
        port = protocol.defaultPort
    }

    parts["host"]?.let { host = it }
    parts["port"]?.let { port = it.toInt() }

    parts["encodedPath"]?.let { encodedPath = it }
    parts["user"]?.let { user = it}
    parts["password"]?.let { password = it}
    parts["fragment"]?.let { fragment = it}

    parts["parameters"]?.let { rawParams ->
        val rawParameters = parseQueryString(rawParams)
        rawParameters.forEach { key, values ->
            parameters.appendAll(key, values)
        }
    }

    return this
}

fun URLBuilder.takeFrom(url: URLBuilder): URLBuilder {
    protocol = url.protocol
    host = url.host
    port = url.port
    encodedPath = url.encodedPath
    user = url.user
    password = url.password
    parameters.appendAll(url.parameters)
    fragment = url.fragment
    trailingQuery = url.trailingQuery

    return this
}

fun URLBuilder.takeFrom(url: Url): URLBuilder {
    protocol = url.protocol
    host = url.host
    port = url.port
    encodedPath = url.encodedPath
    user = url.user
    password = url.password
    parameters.appendAll(url.parameters)
    fragment = url.fragment
    trailingQuery = url.trailingQuery

    return this
}

val Url.fullPath: String
    get() = buildString { appendUrlFullPath(encodedPath, parameters, trailingQuery) }

val Url.hostWithPort: String get() = "$host:$port"

internal fun Appendable.appendUrlFullPath(
    encodedPath: String,
    queryParameters: Parameters,
    trailingQuery: Boolean
) {
    if (!encodedPath.startsWith("/")) {
        append('/')
    }

    append(encodedPath)

    if (!queryParameters.isEmpty() || trailingQuery) {
        append("?")
    }

    queryParameters.formUrlEncodeTo(this)
}
