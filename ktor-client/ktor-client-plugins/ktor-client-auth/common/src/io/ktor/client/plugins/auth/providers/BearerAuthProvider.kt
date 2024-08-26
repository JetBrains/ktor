/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.utils.io.*

/**
 * Installs the client's [BearerAuthProvider].
 */
public fun AuthConfig.bearer(block: BearerAuthConfig.() -> Unit) {
    with(BearerAuthConfig().apply(block)) {
        this@bearer.providers.add(BearerAuthProvider(_refreshTokens, _loadTokens, _sendWithoutRequest, realm))
    }
}

public class BearerTokens(
    public val accessToken: String,
    public val refreshToken: String?
)

/**
 * Parameters to be passed to [BearerAuthConfig.refreshTokens] lambda.
 */
public class RefreshTokensParams(
    public val client: HttpClient,
    public val response: HttpResponse,
    public val oldTokens: BearerTokens?
) {

    /**
     * Marks that this request is for refreshing auth tokens, resulting in a special handling of it.
     */
    public fun HttpRequestBuilder.markAsRefreshTokenRequest() {
        attributes.put(AuthCircuitBreaker, Unit)
    }
}

/**
 * A configuration for [BearerAuthProvider].
 */
@KtorDsl
public class BearerAuthConfig {
    internal var _refreshTokens: suspend RefreshTokensParams.() -> BearerTokens? = { null }
    internal var _loadTokens: suspend () -> BearerTokens? = { null }
    internal var _sendWithoutRequest: (HttpRequestBuilder) -> Boolean = { true }

    public var realm: String? = null

    /**
     * Configures a callback that refreshes a token when the 401 status code is received.
     */
    public fun refreshTokens(block: suspend RefreshTokensParams.() -> BearerTokens?) {
        _refreshTokens = block
    }

    /**
     * Configures a callback that loads a cached token from a local storage.
     * Note: Using the same client instance here to make a request will result in a deadlock.
     */
    public fun loadTokens(block: suspend () -> BearerTokens?) {
        _loadTokens = block
    }

    /**
     * Sends credentials without waiting for [HttpStatusCode.Unauthorized].
     */
    public fun sendWithoutRequest(block: (HttpRequestBuilder) -> Boolean) {
        _sendWithoutRequest = block
    }
}

/**
 * An authentication provider for the Bearer HTTP authentication scheme.
 * Bearer authentication involves security tokens called bearer tokens.
 * As an example, these tokens can be used as a part of OAuth flow to authorize users of your application
 * by using external providers, such as Google, Facebook, Twitter, and so on.
 *
 * You can learn more from [Bearer authentication](https://ktor.io/docs/bearer-client.html).
 */
public class BearerAuthProvider(
    private val refreshTokens: suspend RefreshTokensParams.() -> BearerTokens?,
    loadTokens: suspend () -> BearerTokens?,
    private val sendWithoutRequestCallback: (HttpRequestBuilder) -> Boolean = { true },
    private val realm: String?
) : AuthProvider {

    @Suppress("OverridingDeprecatedMember")
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    private val tokensHolder = AuthTokenHolder(loadTokens)

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = sendWithoutRequestCallback(request)

    /**
     * Checks if current provider is applicable to the request.
     */
    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth.authScheme != AuthScheme.Bearer) {
            LOGGER.trace("Bearer Auth Provider is not applicable for $auth")
            return false
        }
        val isSameRealm = when {
            realm == null -> true
            auth !is HttpAuthHeader.Parameterized -> false
            else -> auth.parameter("realm") == realm
        }
        if (!isSameRealm) {
            LOGGER.trace("Bearer Auth Provider is not applicable for this realm")
        }
        return isSameRealm
    }

    /**
     * Adds an authentication method headers and credentials.
     */
    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        val token = tokensHolder.loadToken() ?: return

        request.headers {
            val tokenValue = "Bearer ${token.accessToken}"
            if (contains(HttpHeaders.Authorization)) {
                remove(HttpHeaders.Authorization)
            }
            append(HttpHeaders.Authorization, tokenValue)
        }
    }

    public override suspend fun refreshToken(response: HttpResponse): Boolean {
        val newToken = tokensHolder.setToken {
            refreshTokens(RefreshTokensParams(response.call.client, response, tokensHolder.loadToken()))
        }
        return newToken != null
    }

    public fun clearToken() {
        tokensHolder.clearToken()
    }
}
