/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.auth

/**
 * Contains the standard auth schemes.
 */
public object AuthScheme {
    /**
     * Basic Authentication described in the RFC-7617
     *
     * ```
     * response = base64("$user:$password")
     * ```
     *
     * see https://tools.ietf.org/html/rfc7617)
     */
    public const val Basic: String = "Basic"

    /**
     * Digest Authentication described in the RFC-2069:
     *
     * ```
     * HA1 = MD5("$username:$realm:$password") // What's usually stored
     * HA2 = MD5("$method:$digestURI")
     * response = MD5("$HA1:$nonce:$HA2") // The client and the server sends and checks this.
     * ```
     *
     * see https://tools.ietf.org/html/rfc2069
     */
    public const val Digest: String = "Digest"

    /**
     * Described in the RFC-4599:
     *
     * see https://www.ietf.org/rfc/rfc4559.txt
     */
    public const val Negotiate: String = "Negotiate"

    /**
     * OAuth Authentication described in the RFC-6749:
     *
     * see https://tools.ietf.org/html/rfc6749
     */
    public const val OAuth: String = "OAuth"

    /**
     * Bearer Authentication described in the RFC-6749 & RFC6750:
     *
     * see https://tools.ietf.org/html/rfc6749
     * & https://tools.ietf.org/html/rfc6750
     */
    public const val Bearer: String = "Bearer"
}
