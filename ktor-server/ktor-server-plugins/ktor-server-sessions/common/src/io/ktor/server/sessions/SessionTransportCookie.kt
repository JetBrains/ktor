/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.server.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.date.*

public const val DEFAULT_SESSION_MAX_AGE: Long = 7L * 24 * 3600 // 7 days

/**
 * A session transport that adds the `Set-Cookie` header and reads the `Cookie` header
 * for the specified cookie [name], and a specific cookie [configuration] after
 * applying/un-applying the specified transforms defined by [transformers].
 *
 * @property name is a cookie name
 * @property configuration is a cookie configuration
 * @property transformers is a list of session transformers
 */
public class SessionTransportCookie(
    public val name: String,
    public val configuration: CookieConfiguration,
    public val transformers: List<SessionTransportTransformer>
) : SessionTransport {

    override fun receive(call: ApplicationCall): String? {
        return transformers.transformRead(call.request.cookies[name, configuration.encoding])
    }

    override fun send(call: ApplicationCall, value: String) {
        val now = GMTDate()
        val maxAge = configuration.maxAgeInSeconds
        val expires = when (maxAge) {
            null, 0L -> null
            else -> now + maxAge * 1000L
        }

        val cookie = Cookie(
            name,
            transformers.transformWrite(value),
            configuration.encoding,
            maxAge?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            expires,
            configuration.domain,
            configuration.path,
            configuration.secure,
            configuration.httpOnly,
            configuration.extensions
        )

        call.response.cookies.append(cookie)
    }

    override fun clear(call: ApplicationCall) {
        call.response.cookies.append(clearCookie())
    }

    internal fun clearCookie(): Cookie = Cookie(
        name,
        "",
        configuration.encoding,
        maxAge = 0,
        domain = configuration.domain,
        path = configuration.path,
        secure = configuration.secure,
        httpOnly = configuration.httpOnly,
        extensions = configuration.extensions,
        expires = GMTDate.START
    )

    override fun toString(): String {
        return "SessionTransportCookie: $name"
    }
}

/**
 * A configuration used to specify cookie attributes for [Sessions].
 */
public class CookieConfiguration {
    /**
     * Specifies the number of seconds until the cookie expires.
     */
    public var maxAgeInSeconds: Long? = DEFAULT_SESSION_MAX_AGE
        set(newMaxAge) {
            require(newMaxAge == null || newMaxAge >= 0) { "maxAgeInSeconds shouldn't be negative: $newMaxAge" }
            field = newMaxAge
        }

    /**
     * Specifies a cookie encoding.
     */
    public var encoding: CookieEncoding = CookieEncoding.URI_ENCODING

    /**
     * Specifies the host to which the cookie is sent.
     */
    public var domain: String? = null

    /**
     * Cookie path
     *
     * Specifies the cookie path.
     */
    public var path: String? = "/"

    /**
     * Enables transferring cookies via a secure connection only and
     * protects session data from HTTPS downgrade attacks.
     */
    public var secure: Boolean = false

    /**
     * Specifies whether cookie access is forbidden from JavaScript.
     */
    public var httpOnly: Boolean = true

    /**
     * Allows you to add custom cookie attributes, which are not exposed explicitly.
     * For example, you can pass the `SameSite` attribute in the following way:
     * ```kotlin
     * cookie<UserSession>("user_session") {
     *     cookie.extensions["SameSite"] = "lax"
     * }
     * ```
     */
    public val extensions: MutableMap<String, String?> = mutableMapOf()
}
