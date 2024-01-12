/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*

/**
 * OAuth versions used in configuration.
 */
@Suppress("KDocMissingDocumentation")
public enum class OAuthVersion {
    V10a, V20
}

/**
 * OAuth server settings.
 * @property name configuration name
 * @property version OAuth version (1a or 2)
 */
public sealed class OAuthServerSettings(public val name: String, public val version: OAuthVersion) {
    /**
     * OAuth1a server settings
     * @property requestTokenUrl OAuth server token request URL
     * @property authorizeUrl OAuth server authorization page URL
     * @property accessTokenUrl OAuth server access token request URL
     * @property consumerKey consumer key parameter (provided by OAuth server vendor)
     * @property consumerSecret a secret key parameter (provided by OAuth server vendor)
     */
    public class OAuth1aServerSettings(
        name: String,
        public val requestTokenUrl: String,
        public val authorizeUrl: String,
        public val accessTokenUrl: String,

        public val consumerKey: String,
        public val consumerSecret: String,

        public val accessTokenInterceptor: HttpRequestBuilder.() -> Unit = {}
    ) : OAuthServerSettings(name, OAuthVersion.V10a)

    /**
     * OAuth2 server settings
     * @property authorizeUrl OAuth server authorization page URL
     * @property accessTokenUrl OAuth server access token request URL
     * @property requestMethod HTTP request method to be used to acquire access token (see vendors documentation)
     * @property clientId client id parameter (provided by OAuth server vendor)
     * @property clientSecret client secret parameter (provided by OAuth server vendor)
     * @property defaultScopes OAuth scopes used by default
     * @property accessTokenRequiresBasicAuth to send BASIC auth header when an access token is requested
     * @property passParamsInURL whether to pass request parameters in POST requests in URL instead of body.
     * @property nonceManager to be used to produce and verify nonce values
     * @property authorizeUrlInterceptor an interceptor function to customize authorization URL
     * @property extraAuthParameters extra parameters to send during authentication
     * @property extraTokenParameters extra parameters to send with getting access token call
     * @property accessTokenInterceptor an interceptor function to customize access token request
     */
    public class OAuth2ServerSettings(
        name: String,
        public val authorizeUrl: String,
        public val accessTokenUrl: String,
        public val requestMethod: HttpMethod = HttpMethod.Get,

        public val clientId: String,
        public val clientSecret: String,
        public val defaultScopes: List<String> = emptyList(),
        public val accessTokenRequiresBasicAuth: Boolean = false,

        public val nonceManager: NonceManager = GenerateOnlyNonceManager,

        public val authorizeUrlInterceptor: URLBuilder.() -> Unit = {},
        public val passParamsInURL: Boolean = false,
        public val extraAuthParameters: List<Pair<String, String>> = emptyList(),
        public val extraTokenParameters: List<Pair<String, String>> = emptyList(),
        public val accessTokenInterceptor: HttpRequestBuilder.() -> Unit = {},
        public val onStateCreated: suspend (call: ApplicationCall, state: String) -> Unit = { _, _ -> }
    ) : OAuthServerSettings(name, OAuthVersion.V20)
}

/**
 * OAuth callback parameters.
 */
public sealed class OAuthCallback {
    /**
     * An OAuth1a token pair callback parameters.
     * @property token OAuth1a token
     * @property tokenSecret OAuth1a token secret
     */
    public data class TokenPair(val token: String, val tokenSecret: String) : OAuthCallback()

    /**
     * An OAuth2 token callback parameter.
     * @property token OAuth2 token provided by server
     * @property state passed from a client (ktor server) during authorization startup
     */
    public data class TokenSingle(val token: String, val state: String) : OAuthCallback()

    /**
     * Oauth2 error callback parameters
     * @property error the error code passed from the identity provider
     * @property errorDescription optionally passed, human-readable description of the error code
     */
    public data class Error(val error: String, val errorDescription: String?) : OAuthCallback()
}

/**
 * An OAuth access token acquired from the server.
 */
public sealed class OAuthAccessTokenResponse : Principal {
    /**
     * OAuth1a access token acquired from the server
     * @property token itself
     * @property tokenSecret token secret to be used with [token]
     * @property extraParameters contains additional parameters provided by the server
     */
    public data class OAuth1a(
        val token: String,
        val tokenSecret: String,
        val extraParameters: Parameters = Parameters.Empty
    ) : OAuthAccessTokenResponse()

    /**
     * OAuth2 access token acquired from the server
     * @property accessToken access token from server
     * @property tokenType OAuth2 token type (usually Bearer)
     * @property expiresIn token expiration timestamp
     * @property refreshToken to be used to refresh access token after expiration
     * @property state generated state used for the OAuth procedure
     * @property extraParameters contains additional parameters provided by the server
     */
    public data class OAuth2(
        val accessToken: String,
        val tokenType: String,
        val expiresIn: Long,
        val refreshToken: String?,
        val extraParameters: Parameters = Parameters.Empty,
    ) : OAuthAccessTokenResponse() {

        public var state: String? = null
            private set

        public constructor(
            accessToken: String,
            tokenType: String,
            expiresIn: Long,
            refreshToken: String?,
            extraParameters: Parameters = Parameters.Empty,
            state: String? = null
        ) : this(accessToken, tokenType, expiresIn, refreshToken, extraParameters) {
            this.state = state
        }
    }
}

/**
 * OAuth grant types constants.
 */
@Suppress("KDocMissingDocumentation")
public object OAuthGrantTypes {
    public const val AuthorizationCode: String = "authorization_code"
    public const val Password: String = "password"
}
