/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.logging.*

private val Logger: Logger = KtorSimpleLogger("io.ktor.auth.oauth")

/**
 * An OAuth provider key.
 */
public val OAuthKey: Any = "OAuth"

/**
 * An `OAuth` [Authentication] provider.
 *
 * @see [oauth]
 */
public class OAuthAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    internal val client: HttpClient = config.client
    internal val providerLookup: ApplicationCall.() -> OAuthServerSettings? = config.providerLookup
    internal val urlProvider: ApplicationCall.(OAuthServerSettings) -> String = config.urlProvider

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        if (PlatformUtils.IS_JVM) oauth1a(name, context)
        oauth2(name, context)
    }

    /**
     * A configuration for the [oauth] authentication provider.
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        /**
         * An HTTP client instance used to make requests to the OAuth server.
         */
        public lateinit var client: HttpClient

        /**
         * A lookup function to find OAuth server settings for the particular call.
         */
        public lateinit var providerLookup: ApplicationCall.() -> OAuthServerSettings?

        /**
         * Specifies a redirect route that is opened when authorization is completed.
         */
        public lateinit var urlProvider: ApplicationCall.(OAuthServerSettings) -> String

        internal fun build() = OAuthAuthenticationProvider(this)
    }
}

/**
 * Installs the OAuth [Authentication] provider.
 * OAuth can be used to authorize users of your application by using external providers,
 * such as Google, Facebook, Twitter, and so on.
 * To learn how to configure it, see [OAuth](https://ktor.io/docs/oauth.html).
 */
public fun AuthenticationConfig.oauth(
    name: String? = null,
    configure: OAuthAuthenticationProvider.Config.() -> Unit
) {
    val provider = OAuthAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

/**
 * Error container for when the upstream identity provider does not respond with the token credentials, and instead
 * responds with error query parameters.
 */
public class OAuth2RedirectError(public val error: String, public val errorDescription: String?) :
    AuthenticationFailedCause.Error(if (errorDescription == null) error else "$error: $errorDescription")

internal suspend fun OAuthAuthenticationProvider.oauth2(authProviderName: String?, context: AuthenticationContext) {
    val call = context.call
    val provider = call.providerLookup()
    if (provider !is OAuthServerSettings.OAuth2ServerSettings) return

    val callbackResponse = call.oauth2HandleCallback()
    val callbackRedirectUrl = call.urlProvider(provider)
    val cause: AuthenticationFailedCause? = when (callbackResponse) {
        is OAuthCallback.TokenSingle -> oauth2RequestToken(
            authProviderName,
            provider,
            callbackRedirectUrl,
            callbackResponse,
            context
        )
        is OAuthCallback.Error -> OAuth2RedirectError(callbackResponse.error, callbackResponse.errorDescription)
        else -> AuthenticationFailedCause.NoCredentials
    }

    cause ?: return
    @Suppress("NAME_SHADOWING")
    context.challenge(OAuthKey, cause) { challenge, call ->
        val state = provider.nonceManager.newNonce()
        provider.onStateCreated(call, state)
        call.redirectAuthenticateOAuth2(
            provider,
            callbackRedirectUrl,
            state = state,
            scopes = provider.defaultScopes,
            extraParameters = provider.extraAuthParameters,
            interceptor = provider.authorizeUrlInterceptor
        )
        challenge.complete()
    }
}

internal expect suspend fun OAuthAuthenticationProvider.oauth1a(
    authProviderName: String?,
    context: AuthenticationContext
)

private suspend fun OAuthAuthenticationProvider.oauth2RequestToken(
    authProviderName: String?,
    provider: OAuthServerSettings.OAuth2ServerSettings,
    callbackRedirectUrl: String,
    token: OAuthCallback.TokenSingle,
    context: AuthenticationContext
) = try {
    val accessToken = oauth2RequestAccessToken(client, provider, callbackRedirectUrl, token)
    context.principal(authProviderName, accessToken)
    null
} catch (cause: OAuth2Exception.InvalidGrant) {
    Logger.trace("OAuth invalid grant reported: {}", cause)
    AuthenticationFailedCause.InvalidCredentials
} catch (cause: Throwable) {
    Logger.trace("OAuth2 request access token failed", cause)
    context.error(
        OAuthKey,
        AuthenticationFailedCause.Error("Failed to request OAuth2 access token due to $cause")
    )
    null
}
