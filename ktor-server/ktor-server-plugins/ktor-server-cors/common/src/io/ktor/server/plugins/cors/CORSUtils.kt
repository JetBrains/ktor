/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

internal fun ApplicationCall.accessControlAllowOrigin(
    origin: String,
    allowsAnyHost: Boolean,
    allowCredentials: Boolean
) {
    val headerOrigin = if (allowsAnyHost && !allowCredentials) "*" else origin
    response.header(HttpHeaders.AccessControlAllowOrigin, headerOrigin)
}

internal fun ApplicationCall.corsVary() {
    val vary = response.headers[HttpHeaders.Vary]
    val varyValue = if (vary == null) HttpHeaders.Origin else vary + ", " + HttpHeaders.Origin
    response.header(HttpHeaders.Vary, varyValue)
}

internal fun ApplicationCall.accessControlAllowCredentials(allowCredentials: Boolean) {
    if (allowCredentials) {
        response.header(HttpHeaders.AccessControlAllowCredentials, "true")
    }
}

internal fun ApplicationCall.accessControlMaxAge(maxAgeHeaderValue: String?) {
    if (maxAgeHeaderValue != null) {
        response.header(HttpHeaders.AccessControlMaxAge, maxAgeHeaderValue)
    }
}

internal fun isSameOrigin(origin: String, point: RequestConnectionPoint, numberRegex: Regex): Boolean {
    val requestOrigin = "${point.scheme}://${point.serverHost}:${point.serverPort}"
    return normalizeOrigin(requestOrigin, numberRegex) == normalizeOrigin(origin, numberRegex)
}

internal fun corsCheckOrigins(
    origin: String,
    allowsAnyHost: Boolean,
    hostsNormalized: Set<String>,
    hostsWithWildcard: Set<Pair<String, String>>,
    originPredicates: List<(String) -> Boolean>,
    numberRegex: Regex
): Boolean {
    val normalizedOrigin = normalizeOrigin(origin, numberRegex)
    return allowsAnyHost || normalizedOrigin in hostsNormalized || hostsWithWildcard.any { (prefix, suffix) ->
        normalizedOrigin.startsWith(prefix) && normalizedOrigin.endsWith(suffix)
    } || originPredicates.any { it(origin) }
}

internal fun corsCheckRequestHeaders(
    requestHeaders: List<String>,
    allHeadersSet: Set<String>,
    headerPredicates: List<(String) -> Boolean>
): Boolean = requestHeaders.all { header ->
    header in allHeadersSet || headerMatchesAPredicate(header, headerPredicates)
}

internal fun headerMatchesAPredicate(header: String, headerPredicates: List<(String) -> Boolean>): Boolean =
    headerPredicates.any { it(header) }

internal fun ApplicationCall.corsCheckCurrentMethod(methods: Set<HttpMethod>): Boolean = request.httpMethod in methods

internal fun ApplicationCall.corsCheckRequestMethod(methods: Set<HttpMethod>): Boolean {
    val requestMethod = request.header(HttpHeaders.AccessControlRequestMethod)?.let { HttpMethod(it) }
    return requestMethod != null && requestMethod in methods
}

internal suspend fun ApplicationCall.respondCorsFailed() {
    respond(HttpStatusCode.Forbidden)
}

internal fun isValidOrigin(origin: String): Boolean {
    if (origin.isEmpty()) return false
    if (origin == "null") return true
    if ("%" in origin) return false

    val protoDelimiter = origin.indexOf("://")
    if (protoDelimiter <= 0) return false

    val protoValid = origin[0].isLetter() && origin.subSequence(0, protoDelimiter).all { ch ->
        ch.isLetter() || ch.isDigit() || ch == '-' || ch == '+' || ch == '.'
    }

    if (!protoValid) return false

    var portIndex = origin.length
    for (index in protoDelimiter + 3 until origin.length) {
        val ch = origin[index]
        if (ch == ':' || ch == '/') {
            portIndex = index + 1
            break
        }
        if (ch == '?') return false
    }

    for (index in portIndex until origin.length) {
        val isTrailingSlash = index == origin.length - 1 && origin[index] == '/'
        if (!origin[index].isDigit() && !isTrailingSlash) return false
    }

    return true
}

internal fun normalizeOrigin(origin: String, numberRegex: Regex): String {
    if (origin == "null" || origin == "*") return origin

    val builder = StringBuilder(origin.length)
    if (origin.endsWith("/")) {
        builder.append(origin, 0, origin.length - 1)
    } else {
        builder.append(origin)
    }
    if (!builder.toString().substringAfterLast(":", "").matches(numberRegex)) {
        val port = when (builder.toString().substringBefore(':')) {
            "http" -> "80"
            "https" -> "443"
            else -> null
        }

        if (port != null) {
            builder.append(":$port")
        }
    }

    return builder.toString()
}
