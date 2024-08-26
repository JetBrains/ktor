/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.internal.*
import io.ktor.util.logging.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.serialization.json.*

private val Logger: Logger = KtorSimpleLogger("io.ktor.auth.oauth")

internal suspend fun ApplicationCall.oauth2HandleCallback(): OAuthCallback? {
    val params = when (request.contentType()) {
        ContentType.Application.FormUrlEncoded -> receiveParameters()
        else -> parameters
    }
    val code = params[OAuth2RequestParameters.Code]
    val state = params[OAuth2RequestParameters.State]
    val error = params[OAuth2RequestParameters.Error]
    val errorDescription = params[OAuth2RequestParameters.ErrorDescription]

    return when {
        error != null -> OAuthCallback.Error(error, errorDescription)
        code != null && state != null -> OAuthCallback.TokenSingle(code, state)
        else -> null
    }
}

internal suspend fun ApplicationCall.redirectAuthenticateOAuth2(
    settings: OAuthServerSettings.OAuth2ServerSettings,
    callbackRedirectUrl: String,
    state: String,
    extraParameters: List<Pair<String, String>> = emptyList(),
    scopes: List<String> = emptyList(),
    interceptor: URLBuilder.() -> Unit
) {
    redirectAuthenticateOAuth2(
        authenticateUrl = settings.authorizeUrl,
        callbackRedirectUrl = callbackRedirectUrl,
        clientId = settings.clientId,
        state = state,
        scopes = scopes,
        parameters = extraParameters,
        interceptor = interceptor
    )
}

internal suspend fun oauth2RequestAccessToken(
    client: HttpClient,
    settings: OAuthServerSettings.OAuth2ServerSettings,
    usedRedirectUrl: String,
    callbackResponse: OAuthCallback.TokenSingle,
    configure: (HttpRequestBuilder.() -> Unit)? = null
): OAuthAccessTokenResponse.OAuth2 {
    val interceptor: HttpRequestBuilder.() -> Unit = when (configure) {
        null -> settings.accessTokenInterceptor
        else -> fun HttpRequestBuilder.() {
            settings.accessTokenInterceptor(this)
            configure()
        }
    }

    return oauth2RequestAccessToken(
        client,
        settings.requestMethod,
        usedRedirectUrl,
        settings.accessTokenUrl,
        settings.clientId,
        settings.clientSecret,
        callbackResponse.state,
        callbackResponse.token,
        settings.extraTokenParameters,
        interceptor,
        settings.accessTokenRequiresBasicAuth,
        settings.nonceManager,
        settings.passParamsInURL
    )
}

private suspend fun ApplicationCall.redirectAuthenticateOAuth2(
    authenticateUrl: String,
    callbackRedirectUrl: String,
    clientId: String,
    state: String,
    scopes: List<String>,
    parameters: List<Pair<String, String>>,
    interceptor: URLBuilder.() -> Unit
) {
    val url = URLBuilder()
    url.takeFrom(authenticateUrl)
    url.parameters.apply {
        append(OAuth2RequestParameters.ClientId, clientId)
        append(OAuth2RequestParameters.RedirectUri, callbackRedirectUrl)
        if (scopes.isNotEmpty()) {
            append(OAuth2RequestParameters.Scope, scopes.joinToString(" "))
        }
        append(OAuth2RequestParameters.State, state)
        append(OAuth2RequestParameters.ResponseType, "code")
        parameters.forEach { (k, v) -> append(k, v) }
    }
    interceptor(url)

    return respondRedirect(url.buildString())
}

private suspend fun oauth2RequestAccessToken(
    client: HttpClient,
    method: HttpMethod,
    usedRedirectUrl: String?,
    baseUrl: String,
    clientId: String,
    clientSecret: String,
    state: String?,
    code: String?,
    extraParameters: List<Pair<String, String>> = emptyList(),
    configure: HttpRequestBuilder.() -> Unit = {},
    useBasicAuth: Boolean = false,
    nonceManager: NonceManager,
    passParamsInURL: Boolean = false,
    grantType: String = OAuthGrantTypes.AuthorizationCode
): OAuthAccessTokenResponse.OAuth2 {
    if (!nonceManager.verifyNonce(state.orEmpty())) {
        throw OAuth2Exception.InvalidNonce()
    }

    val request = HttpRequestBuilder()
    request.url.takeFrom(baseUrl)

    val urlParameters = ParametersBuilder().apply {
        append(OAuth2RequestParameters.ClientId, clientId)
        append(OAuth2RequestParameters.ClientSecret, clientSecret)
        append(OAuth2RequestParameters.GrantType, grantType)
        if (state != null) {
            append(OAuth2RequestParameters.State, state)
        }
        if (code != null) {
            append(OAuth2RequestParameters.Code, code)
        }
        if (usedRedirectUrl != null) {
            append(OAuth2RequestParameters.RedirectUri, usedRedirectUrl)
        }
        extraParameters.forEach { (k, v) -> append(k, v) }
    }.build()

    when (method) {
        HttpMethod.Get -> request.url.parameters.appendAll(urlParameters)
        HttpMethod.Post -> {
            if (passParamsInURL) {
                request.url.parameters.appendAll(urlParameters)
            } else {
                request.setBody(
                    TextContent(
                        urlParameters.formUrlEncode(),
                        ContentType.Application.FormUrlEncoded
                    )
                )
            }
        }

        else -> throw UnsupportedOperationException("Method $method is not supported. Use GET or POST")
    }

    request.apply {
        this.method = method
        header(
            HttpHeaders.Accept,
            listOf(ContentType.Application.FormUrlEncoded, ContentType.Application.Json).joinToString(",")
        )
        if (useBasicAuth) {
            header(
                HttpHeaders.Authorization,
                HttpAuthHeader.Single(
                    AuthScheme.Basic,
                    "$clientId:$clientSecret".toByteArray(Charsets.ISO_8859_1).encodeBase64()
                ).render()
            )
        }

        configure()
    }

    val response = client.request(request)

    val body = response.bodyAsText()

    val (contentType, content) = try {
        if (response.status == HttpStatusCode.NotFound) {
            throw IOException("Access token query failed with http status 404 for the page $baseUrl")
        }
        val contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ContentType.Any

        Pair(contentType, body)
    } catch (ioe: IOException) {
        throw ioe
    } catch (cause: Throwable) {
        throw IOException("Failed to acquire request token due to wrong content: $body", cause)
    }

    val contentDecodeResult = Result.runCatching { decodeContent(content, contentType) }
    val errorCode = contentDecodeResult.map { it[OAuth2ResponseParameters.Error] }

    // try error code first
    errorCode.getOrNull()?.let {
        throwOAuthError(it, contentDecodeResult.getOrThrow())
    }

    // ensure status code is successful
    if (!response.status.isSuccess()) {
        throw IOException(
            "Access token query failed with http status ${response.status} for the page $baseUrl"
        )
    }

    // will fail if content decode failed but status is OK
    val contentDecoded = contentDecodeResult.getOrThrow()

    // finally, extract access token
    return OAuthAccessTokenResponse.OAuth2(
        accessToken = contentDecoded[OAuth2ResponseParameters.AccessToken]
            ?: throw OAuth2Exception.MissingAccessToken(),
        tokenType = contentDecoded[OAuth2ResponseParameters.TokenType] ?: "",
        state = state,
        expiresIn = contentDecoded[OAuth2ResponseParameters.ExpiresIn]?.toLong() ?: 0L,
        refreshToken = contentDecoded[OAuth2ResponseParameters.RefreshToken],
        extraParameters = contentDecoded
    )
}

private fun decodeContent(content: String, contentType: ContentType): Parameters = when {
    contentType.match(ContentType.Application.FormUrlEncoded) -> content.parseUrlEncodedParameters()
    contentType.match(ContentType.Application.Json) -> Parameters.build {
        Json.decodeFromString(JsonObject.serializer(), content).forEach { (key, element) ->
            (element as? JsonPrimitive)?.content?.let { append(key, it) }
        }
    }

    else -> {
        // some servers may respond with a wrong content type, so we have to try to guess
        when {
            content.startsWith("{") && content.trim().endsWith("}") -> decodeContent(
                content.trim(),
                ContentType.Application.Json
            )

            content.matches("([a-zA-Z\\d_-]+=[^=&]+&?)+".toRegex()) -> decodeContent(
                content,
                ContentType.Application.FormUrlEncoded
            ) // TODO too risky, isn't it?
            else -> throw IOException("unsupported content type $contentType")
        }
    }
}

/**
 * Implements Resource Owner Password Credentials Grant.
 *
 * Takes [UserPasswordCredential] and validates it using OAuth2 sequence, provides [OAuthAccessTokenResponse.OAuth2] if succeeds.
 */
public suspend fun verifyWithOAuth2(
    credential: UserPasswordCredential,
    client: HttpClient,
    settings: OAuthServerSettings.OAuth2ServerSettings
): OAuthAccessTokenResponse.OAuth2 {
    return oauth2RequestAccessToken(
        client, HttpMethod.Post,
        usedRedirectUrl = null,
        baseUrl = settings.accessTokenUrl,
        clientId = settings.clientId,
        clientSecret = settings.clientSecret,
        code = null,
        state = null,
        configure = settings.accessTokenInterceptor,
        extraParameters = listOf(
            OAuth2RequestParameters.UserName to credential.name,
            OAuth2RequestParameters.Password to credential.password
        ),
        useBasicAuth = true,
        nonceManager = settings.nonceManager,
        passParamsInURL = settings.passParamsInURL,
        grantType = OAuthGrantTypes.Password
    )
}

/**
 * List of OAuth2 request parameters for both peers.
 */

public object OAuth2RequestParameters {
    public const val ClientId: String = "client_id"
    public const val Scope: String = "scope"
    public const val ClientSecret: String = "client_secret"
    public const val GrantType: String = "grant_type"
    public const val Code: String = "code"
    public const val Error: String = "error"
    public const val ErrorDescription: String = "error_description"
    public const val State: String = "state"
    public const val RedirectUri: String = "redirect_uri"
    public const val ResponseType: String = "response_type"
    public const val UserName: String = "username"
    public const val Password: String = "password"
}

/**
 * List of OAuth2 server response parameters.
 */

public object OAuth2ResponseParameters {
    public const val AccessToken: String = "access_token"
    public const val TokenType: String = "token_type"
    public const val ExpiresIn: String = "expires_in"
    public const val RefreshToken: String = "refresh_token"
    public const val Error: String = "error"
    public const val ErrorDescription: String = "error_description"
}

private fun throwOAuthError(errorCode: String, parameters: Parameters): Nothing {
    val errorDescription = parameters["error_description"] ?: "OAuth2 Server responded with $errorCode"

    throw when (errorCode) {
        "invalid_grant" -> OAuth2Exception.InvalidGrant(errorDescription)
        else -> OAuth2Exception.UnknownException(errorDescription, errorCode)
    }
}

/**
 * Represents an error during communicating to OAuth2 server.
 * @property errorCode OAuth2 server replied with
 */
public sealed class OAuth2Exception(message: String, public val errorCode: String?) : Exception(message) {
    /**
     * Thrown when OAuth2 server responds with the "invalid_grant" error.
     */
    public class InvalidGrant(message: String) : OAuth2Exception(message, "invalid_grant")

    /**
     * Thrown when a nonce verification failed.
     */
    public class InvalidNonce : OAuth2Exception("Nonce verification failed", null)

    /**
     * Thrown when an OAuth2 server responds with a successful HTTP status and expected content type that was successfully
     * decoded but the response doesn't contain a error code nor access token.
     */
    public class MissingAccessToken : OAuth2Exception(
        "OAuth2 server response is OK neither error nor access token provided",
        null
    )

    /**
     * Thrown when an OAuth2 server responds with the "unsupported_grant_type" error.
     * @param grantType that was passed to the server
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public class UnsupportedGrantType(public val grantType: String) :
        OAuth2Exception(
            "OAuth2 server doesn't support grant type $grantType",
            "unsupported_grant_type"
        ),
        CopyableThrowable<UnsupportedGrantType> {
        override fun createCopy(): UnsupportedGrantType = UnsupportedGrantType(grantType).also {
            it.initCauseBridge(this)
        }
    }

    /**
     * Thrown when an OAuth2 server responds with [errorCode].
     * @param errorCode the OAuth2 server replied with
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public class UnknownException(
        private val details: String,
        errorCode: String
    ) : OAuth2Exception("$details (error code = $errorCode)", errorCode), CopyableThrowable<UnknownException> {
        override fun createCopy(): UnknownException = UnknownException(details, errorCode!!).also {
            it.initCauseBridge(this)
        }
    }
}
