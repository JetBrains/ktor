/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.serialization.*
import kotlin.jvm.*

/**
 * Represents a cookie with name, content and a set of settings such as expiration, visibility and security.
 * A cookie with neither [expires] nor [maxAge] is a session cookie.
 *
 * @property name
 * @property value
 * @property encoding - cookie encoding type [CookieEncoding]
 * @property maxAge number of seconds to keep cookie
 * @property expires date when it expires
 * @property domain for which it is set
 * @property path for which it is set
 * @property secure send it via secure connection only
 * @property httpOnly only transfer cookie over HTTP, no access from JavaScript
 * @property extensions additional cookie extensions
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.Cookie)
 */
@Serializable
public data class Cookie(
    val name: String,
    val value: String,
    val encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
    @get:JvmName("getMaxAgeInt")
    val maxAge: Int? = null,
    val expires: GMTDate? = null,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val extensions: Map<String, String?> = emptyMap()
)

/**
 * Cooke encoding strategy
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CookieEncoding)
 */
public enum class CookieEncoding {
    /**
     * No encoding (could be dangerous)
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CookieEncoding.RAW)
     */
    RAW,

    /**
     * Double quotes with slash-escaping
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CookieEncoding.DQUOTES)
     */
    DQUOTES,

    /**
     * URI encoding
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CookieEncoding.URI_ENCODING)
     */
    URI_ENCODING,

    /**
     * BASE64 encoding
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CookieEncoding.BASE64_ENCODING)
     */
    BASE64_ENCODING
}

private val loweredPartNames = setOf("max-age", "expires", "domain", "path", "secure", "httponly", "\$x-enc")

/**
 * Parse server's `Set-Cookie` header value
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.parseServerSetCookieHeader)
 */
public fun parseServerSetCookieHeader(cookiesHeader: String): Cookie {
    val asMap = parseClientCookiesHeader(cookiesHeader, false)
    val first = asMap.entries.first { !it.key.startsWith("$") }
    val encoding = asMap["\$x-enc"]?.let { CookieEncoding.valueOf(it) } ?: CookieEncoding.RAW
    val loweredMap = asMap.mapKeys { it.key.toLowerCasePreservingASCIIRules() }

    return Cookie(
        name = first.key,
        value = decodeCookieValue(first.value, encoding),
        encoding = encoding,
        maxAge = loweredMap["max-age"]?.toIntClamping(),
        expires = loweredMap["expires"]?.fromCookieToGmtDate(),
        domain = loweredMap["domain"],
        path = loweredMap["path"],
        secure = "secure" in loweredMap,
        httpOnly = "httponly" in loweredMap,
        extensions = asMap.filterKeys {
            it.toLowerCasePreservingASCIIRules() !in loweredPartNames && it != first.key
        }
    )
}

private val clientCookieHeaderPattern = """(^|;)\s*([^;=\{\}\s]+)\s*(=\s*("[^"]*"|[^;]*))?""".toRegex()

/**
 * Parse client's `Cookie` header value
 */
public fun parseClientCookiesHeader(cookiesHeader: String, skipEscaped: Boolean = true): Map<String, String> =
    clientCookieHeaderPattern.findAll(cookiesHeader)
        .map { (it.groups[2]?.value ?: "") to (it.groups[4]?.value ?: "") }
        .filter { !skipEscaped || !it.first.startsWith("$") }
        .map { cookie ->
            if (cookie.second.startsWith("\"") && cookie.second.endsWith("\"")) {
                cookie.copy(second = cookie.second.removeSurrounding("\""))
            } else {
                cookie
            }
        }
        .toMap()

/**
 * Format `Set-Cookie` header value
 */
public fun renderSetCookieHeader(cookie: Cookie): String = with(cookie) {
    renderSetCookieHeader(
        name,
        value,
        encoding,
        maxAge,
        expires,
        domain,
        path,
        secure,
        httpOnly,
        extensions
    )
}

/**
 * Format `Cookie` header value
 */
public fun renderCookieHeader(cookie: Cookie): String = with(cookie) {
    "$name=${encodeCookieValue(value, encoding)}"
}

/**
 * Format `Set-Cookie` header value
 */
public fun renderSetCookieHeader(
    name: String,
    value: String,
    encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
    maxAge: Int? = null,
    expires: GMTDate? = null,
    domain: String? = null,
    path: String? = null,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    extensions: Map<String, String?> = emptyMap(),
    includeEncoding: Boolean = true
): String = (
    listOf(
        cookiePart(name.assertCookieName(), value, encoding),
        cookiePartUnencoded("Max-Age", maxAge),
        cookiePartUnencoded("Expires", expires?.toHttpDate()),
        cookiePart("Domain", domain, CookieEncoding.RAW),
        cookiePart("Path", path, CookieEncoding.RAW),

        cookiePartFlag("Secure", secure),
        cookiePartFlag("HttpOnly", httpOnly)
    ) + extensions.map { cookiePartExt(it.key.assertCookieName(), it.value) } +
        if (includeEncoding) cookiePartExt("\$x-enc", encoding.name) else ""
    ).filter { it.isNotEmpty() }
    .joinToString("; ")

/**
 * Encode cookie value using the specified [encoding]
 */
public fun encodeCookieValue(value: String, encoding: CookieEncoding): String = when (encoding) {
    CookieEncoding.RAW -> value
    CookieEncoding.DQUOTES -> when {
        value.contains('"') -> throw IllegalArgumentException(
            "The cookie value contains characters that cannot be encoded in DQUOTES format. " +
                "Consider URL_ENCODING mode"
        )
        value.any { it.shouldEscapeInCookies() } -> "\"$value\""
        else -> value
    }
    CookieEncoding.BASE64_ENCODING -> value.encodeBase64()
    CookieEncoding.URI_ENCODING -> value.encodeURLParameter(spaceToPlus = true)
}

/**
 * Decode cookie value using the specified [encoding]
 */
public fun decodeCookieValue(encodedValue: String, encoding: CookieEncoding): String = when (encoding) {
    CookieEncoding.RAW, CookieEncoding.DQUOTES -> when {
        encodedValue.trimStart().startsWith("\"") && encodedValue.trimEnd().endsWith("\"") ->
            encodedValue.trim().removeSurrounding("\"")
        else -> encodedValue
    }
    CookieEncoding.URI_ENCODING -> encodedValue.decodeURLQueryComponent(plusIsSpace = true)
    CookieEncoding.BASE64_ENCODING -> encodedValue.decodeBase64String()
}

private fun String.assertCookieName() = when {
    any { it.shouldEscapeInCookies() } -> throw IllegalArgumentException("Cookie name is not valid: $this")
    else -> this
}

private val cookieCharsShouldBeEscaped = setOf(';', ',', '"')

private fun Char.shouldEscapeInCookies() = isWhitespace() || this < ' ' || this in cookieCharsShouldBeEscaped

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePart(name: String, value: Any?, encoding: CookieEncoding) =
    if (value != null) "$name=${encodeCookieValue(value.toString(), encoding)}" else ""

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartUnencoded(name: String, value: Any?) =
    if (value != null) "$name=$value" else ""

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartFlag(name: String, value: Boolean) =
    if (value) name else ""

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartExt(name: String, value: String?) =
    if (value == null) cookiePartFlag(name, true) else cookiePart(name, value, CookieEncoding.RAW)

private fun String.toIntClamping(): Int = toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
