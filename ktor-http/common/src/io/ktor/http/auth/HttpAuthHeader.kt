/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.auth

import io.ktor.http.*
import io.ktor.http.parsing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

private val TOKEN_EXTRA = setOf('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~')
private val TOKEN68_EXTRA = setOf('-', '.', '_', '~', '+', '/')
private val token68Pattern = "[a-zA-Z0-9\\-._~+/]+=*".toRegex()
private val escapeRegex: Regex = "\\\\.".toRegex()

/**
 * Parses an authorization header [headerValue] into a [HttpAuthHeader].
 * @return [HttpAuthHeader] or `null` if argument string is blank.
 * @throws [ParseException] on invalid header
 *
 * @see [parseAuthorizationHeaders]
 */
public fun parseAuthorizationHeader(headerValue: String): HttpAuthHeader? {
    var index = 0
    index = headerValue.skipSpaces(index)

    val tokenStartIndex = index
    while (index < headerValue.length && headerValue[index].isToken()) {
        index++
    }

    // Auth scheme
    val authScheme = headerValue.substring(tokenStartIndex until index)
    index = headerValue.skipSpaces(index)

    if (authScheme.isBlank()) {
        return null
    }

    if (headerValue.length == index) {
        return HttpAuthHeader.Parameterized(authScheme, emptyList())
    }

    val token68EndIndex = matchToken68(headerValue, index)
    val token68 = headerValue.substring(index until token68EndIndex).trim()
    if (token68.isNotEmpty()) {
        if (token68EndIndex == headerValue.length) {
            return HttpAuthHeader.Single(authScheme, token68)
        }
    }

    val parameters = mutableMapOf<String, String>()
    val endIndex = matchParameters(headerValue, index, parameters)
    return if (endIndex == -1) {
        HttpAuthHeader.Parameterized(authScheme, parameters)
    } else {
        throw ParseException("Function parseAuthorizationHeader can parse only one header")
    }
}

/**
 * Parses an authorization header [headerValue] into a list of [HttpAuthHeader].
 * @return a list of [HttpAuthHeader]
 * @throws [ParseException] on invalid header
 */
@InternalAPI
public fun parseAuthorizationHeaders(headerValue: String): List<HttpAuthHeader> {
    var index = 0
    val headers = mutableListOf<HttpAuthHeader>()
    while (index != -1) {
        index = parseAuthorizationHeader(headerValue, index, headers)
    }
    return headers
}

private fun parseAuthorizationHeader(
    headerValue: String,
    startIndex: Int,
    headers: MutableList<HttpAuthHeader>
): Int {
    var index = headerValue.skipSpaces(startIndex)

    // Auth scheme
    val schemeStartIndex = index
    while (index < headerValue.length && headerValue[index].isToken()) {
        index++
    }
    val authScheme = headerValue.substring(schemeStartIndex until index)

    if (authScheme.isBlank()) {
        throw ParseException("Invalid authScheme value: it should be token, can't be blank")
    }
    index = headerValue.skipSpaces(index)

    nextChallengeIndex(headers, HttpAuthHeader.Parameterized(authScheme, emptyList()), index, headerValue)?.let {
        return it
    }

    val token68EndIndex = matchToken68(headerValue, index)
    val token68 = headerValue.substring(index until token68EndIndex).trim()
    if (token68.isNotEmpty()) {
        nextChallengeIndex(headers, HttpAuthHeader.Single(authScheme, token68), token68EndIndex, headerValue)?.let {
            return it
        }
    }

    val parameters = mutableMapOf<String, String>()
    val nextIndexChallenge = matchParameters(headerValue, index, parameters)
    headers.add(HttpAuthHeader.Parameterized(authScheme, parameters))
    return nextIndexChallenge
}

/**
 * Check for the ending of the current challenge in a header
 * @return -1 if at the end of the header
 * @return null if the challenge is not ended
 * @return a positive number - the index of the beginning of the next challenge
 */
private fun nextChallengeIndex(
    headers: MutableList<HttpAuthHeader>,
    header: HttpAuthHeader,
    index: Int,
    headerValue: String
): Int? {
    if (index == headerValue.length || headerValue[index] == ',') {
        headers.add(header)
        return when {
            index == headerValue.length -> -1
            headerValue[index] == ',' -> index + 1
            else -> error("") // unreachable code
        }
    }
    return null
}

private fun matchParameters(headerValue: String, startIndex: Int, parameters: MutableMap<String, String>): Int {
    var index = startIndex
    while (index > 0 && index < headerValue.length) {
        val nextIndex = matchParameter(headerValue, index, parameters)
        if (nextIndex == index) {
            return index
        } else {
            index = headerValue.skipDelimiter(nextIndex, ',')
        }
    }

    return index
}

private fun matchParameter(
    headerValue: String,
    startIndex: Int,
    parameters: MutableMap<String, String>
): Int {
    val keyStart = headerValue.skipSpaces(startIndex)
    var index = keyStart

    // Take key
    while (index < headerValue.length && headerValue[index].isToken()) {
        index++
    }
    val key = headerValue.substring(keyStart until index)

    // Check if new challenge
    index = headerValue.skipSpaces(index)
    if (index == headerValue.length || headerValue[index] != '=') {
        return startIndex
    }

    // Take '='
    index++
    index = headerValue.skipSpaces(index)

    // Take value
    var quoted = false
    var valueStart = index

    if (headerValue[index] == '"') {
        quoted = true
        index++
        valueStart = index

        var escaped = false
        while (index < headerValue.length) {
            if (headerValue[index] == '"' && !escaped) break
            escaped = !escaped && headerValue[index] == '\\'

            index++
        }

        if (index == headerValue.length) {
            throw ParseException("Expected closing quote'\"' in parameter")
        }
    } else {
        while (index < headerValue.length && headerValue[index] != ' ' && headerValue[index] != ',') {
            index++
        }
    }

    val value = headerValue.substring(valueStart until index)
    parameters[key] = if (quoted) value.unescaped() else value

    if (quoted) index++
    return index
}

private fun matchToken68(headerValue: String, startIndex: Int): Int {
    var index = headerValue.skipSpaces(startIndex)

    while (index < headerValue.length && headerValue[index].isToken68()) {
        index++
    }

    while (index < headerValue.length && headerValue[index] == '=') {
        index++
    }

    return headerValue.skipSpaces(index)
}

/**
 * Describes an authentication header with a mandatory [authScheme] that usually is a standard [AuthScheme].
 *
 * This can be of type [HttpAuthHeader.Single] or [HttpAuthHeader.Parameterized].
 *
 * @property authScheme auth scheme, usually one of [AuthScheme]
 */
public sealed class HttpAuthHeader(public val authScheme: String) {
    init {
        if (!authScheme.matches(token68Pattern)) {
            throw ParseException("Invalid authScheme value: it should be token, but instead it is $authScheme")
        }
    }

    /**
     * Describes an authentication header that is represented by a single [blob].
     * @property blob contains single token 68, should consist from digits, letters and one of the following: `-._~+/`
     */
    public class Single(authScheme: String, public val blob: String) : HttpAuthHeader(authScheme) {
        init {
            if (!blob.matches(token68Pattern)) {
                throw ParseException("Invalid blob value: it should be token68")
            }
        }

        override fun render(): String = "$authScheme $blob"
        override fun render(encoding: HeaderValueEncoding): String = render()

        override fun equals(other: Any?): Boolean {
            if (other !is Single) return false
            return other.authScheme.equals(authScheme, ignoreCase = true) &&
                other.blob.equals(blob, ignoreCase = true)
        }

        override fun hashCode(): Int {
            return Hash.combine(authScheme.lowercase(), blob.lowercase())
        }
    }

    /**
     * Describes a parameterized authentication header that is represented by a set of [parameters] encoded with [encoding].
     * @property parameters a list of auth parameters
     * @property encoding parameters encoding method, one of [HeaderValueEncoding]
     */
    public class Parameterized(
        authScheme: String,
        public val parameters: List<HeaderValueParam>,
        public val encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED
    ) : HttpAuthHeader(authScheme) {
        public constructor(
            authScheme: String,
            parameters: Map<String, String>,
            encoding: HeaderValueEncoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED
        ) : this(authScheme, parameters.entries.map { HeaderValueParam(it.key, it.value) }, encoding)

        init {
            parameters.forEach {
                if (!it.name.matches(token68Pattern)) {
                    throw ParseException("Parameter name should be a token")
                }
            }
        }

        /**
         * Copies this [Parameterized] appending a new parameter [name] [value].
         */
        public fun withParameter(name: String, value: String): Parameterized =
            Parameterized(authScheme, this.parameters + HeaderValueParam(name, value), encoding)

        /**
         * Copies this [Parameterized] replacing parameters with [name] assigning new [value]
         * or appending if no such parameters found.
         * If there were several pairs they will be reduced into a single pair
         * at position of first occurrence discarding following pairs with this [name].
         */
        public fun withReplacedParameter(name: String, value: String): Parameterized {
            val firstIndex = parameters.indexOfFirst { it.name == name }
            if (firstIndex == -1) return withParameter(name, value)

            var replaced = false
            val newParameters = parameters.mapNotNull {
                when {
                    it.name != name -> it
                    !replaced -> {
                        replaced = true
                        HeaderValueParam(name, value)
                    }
                    else -> null
                }
            }

            return Parameterized(authScheme, newParameters, encoding)
        }

        override fun render(encoding: HeaderValueEncoding): String = if (parameters.isEmpty()) {
            authScheme
        } else {
            parameters.joinToString(", ", prefix = "$authScheme ") { "${it.name}=${it.value.encode(encoding)}" }
        }

        /**
         * Tries to extract the first value of a parameter [name]. Returns null when not found.
         */
        public fun parameter(name: String): String? = parameters.firstOrNull { it.name == name }?.value

        private fun String.encode(encoding: HeaderValueEncoding) = when (encoding) {
            HeaderValueEncoding.QUOTED_WHEN_REQUIRED -> escapeIfNeeded()
            HeaderValueEncoding.QUOTED_ALWAYS -> quote()
            HeaderValueEncoding.URI_ENCODE -> encodeURLParameter()
        }

        override fun render(): String = render(encoding)

        override fun equals(other: Any?): Boolean {
            if (other !is Parameterized) return false
            return other.authScheme.equals(authScheme, ignoreCase = true) &&
                other.parameters == parameters
        }

        override fun hashCode(): Int {
            return Hash.combine(authScheme.lowercase(), parameters)
        }
    }

    /**
     * Encodes the header with a specified [encoding].
     */
    public abstract fun render(encoding: HeaderValueEncoding): String

    /**
     * Encodes the header with the default [HeaderValueEncoding] for this header.
     */
    public abstract fun render(): String

    /**
     * Encodes the header with the default [HeaderValueEncoding] for this header.
     */
    override fun toString(): String {
        return render()
    }

    public companion object {
        /**
         * Generates an [AuthScheme.Basic] challenge as a [HttpAuthHeader].
         */
        public fun basicAuthChallenge(realm: String, charset: Charset?): Parameterized = Parameterized(
            AuthScheme.Basic,
            LinkedHashMap<String, String>().apply {
                put(Parameters.Realm, realm)
                if (charset != null) {
                    put(Parameters.Charset, charset.name)
                }
            }
        )

        /**
         * Generates an [AuthScheme.Bearer] challenge as a [HttpAuthHeader].
         */
        public fun bearerAuthChallenge(scheme: String, realm: String? = null): HttpAuthHeader = Parameterized(
            authScheme = scheme,
            parameters = if (realm == null) emptyMap() else mapOf(Parameters.Realm to realm)
        )

        /**
         * Generates an [AuthScheme.Digest] challenge as a [HttpAuthHeader].
         */
        public fun digestAuthChallenge(
            realm: String,
            nonce: String = generateNonce(),
            domain: List<String> = emptyList(),
            opaque: String? = null,
            stale: Boolean? = null,
            algorithm: String = "MD5"
        ): Parameterized = Parameterized(
            AuthScheme.Digest,
            linkedMapOf<String, String>().apply {
                put("realm", realm)
                put("nonce", nonce)
                if (domain.isNotEmpty()) {
                    put("domain", domain.joinToString(" "))
                }
                if (opaque != null) {
                    put("opaque", opaque)
                }
                if (stale != null) {
                    put("stale", stale.toString())
                }
                put("algorithm", algorithm)
            },
            HeaderValueEncoding.QUOTED_ALWAYS
        )
    }

    /**
     * Standard parameters for [Parameterized] [HttpAuthHeader].
     */
    @Suppress("KDocMissingDocumentation", "PublicApiImplicitType")
    public object Parameters {
        public const val Realm: String = "realm"
        public const val Charset: String = "charset"

        public const val OAuthCallback: String = "oauth_callback"
        public const val OAuthConsumerKey: String = "oauth_consumer_key"
        public const val OAuthNonce: String = "oauth_nonce"
        public const val OAuthToken: String = "oauth_token"
        public const val OAuthTokenSecret: String = "oauth_token_secret"
        public const val OAuthVerifier: String = "oauth_verifier"
        public const val OAuthSignatureMethod: String = "oauth_signature_method"
        public const val OAuthTimestamp: String = "oauth_timestamp"
        public const val OAuthVersion: String = "oauth_version"
        public const val OAuthSignature: String = "oauth_signature"
        public const val OAuthCallbackConfirmed: String = "oauth_callback_confirmed"
    }
}

private fun String.unescaped() = replace(escapeRegex) { it.value.takeLast(1) }

private fun String.skipDelimiter(startIndex: Int, delimiter: Char): Int {
    var index = skipSpaces(startIndex)

    if (index == length) return -1
    if (this[index] != delimiter) {
        throw ParseException("Expected delimiter $delimiter at position $index")
    }

    index++
    return skipSpaces(index)
}

private fun String.skipSpaces(startIndex: Int): Int {
    var index = startIndex
    while (index < length && (this[index] == ' ')) {
        index++
    }

    return index
}

private fun Char.isToken68(): Boolean = (this in 'a'..'z') || (this in 'A'..'Z') || isDigit() || this in TOKEN68_EXTRA

private fun Char.isToken(): Boolean = (this in 'a'..'z') || (this in 'A'..'Z') || isDigit() || this in TOKEN_EXTRA
