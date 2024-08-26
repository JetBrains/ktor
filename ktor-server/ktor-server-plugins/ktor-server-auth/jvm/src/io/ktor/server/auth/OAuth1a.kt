/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.errors.*
import kotlinx.io.IOException
import java.time.*
import java.util.*
import javax.crypto.*
import javax.crypto.spec.*

internal fun ApplicationCall.oauth1aHandleCallback(): OAuthCallback.TokenPair? {
    val token = parameters[HttpAuthHeader.Parameters.OAuthToken]
    val verifier = parameters[HttpAuthHeader.Parameters.OAuthVerifier]

    return when {
        token != null && verifier != null -> OAuthCallback.TokenPair(token, verifier)
        else -> null
    }
}

internal suspend fun simpleOAuth1aStep1(
    client: HttpClient,
    settings: OAuthServerSettings.OAuth1aServerSettings,
    callbackUrl: String,
    nonce: String = generateNonce(),
    extraParameters: List<Pair<String, String>> = emptyList()
): OAuthCallback.TokenPair = simpleOAuth1aStep1(
    client,
    settings.consumerSecret + "&",
    settings.requestTokenUrl,
    callbackUrl,
    settings.consumerKey,
    nonce,
    extraParameters
)

private suspend fun simpleOAuth1aStep1(
    client: HttpClient,
    secretKey: String,
    baseUrl: String,
    callback: String,
    consumerKey: String,
    nonce: String = generateNonce(),
    extraParameters: List<Pair<String, String>> = emptyList()
): OAuthCallback.TokenPair {
    val authHeader = createObtainRequestTokenHeaderInternal(
        callback = callback,
        consumerKey = consumerKey,
        nonce = nonce
    ).signInternal(HttpMethod.Post, baseUrl, secretKey, extraParameters)

    val url = baseUrl.appendUrlParameters(extraParameters.formUrlEncode())

    val response = client.post(url) {
        header(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
        header(HttpHeaders.Accept, ContentType.Any.toString())
    }

    val body = response.bodyAsText()
    try {
        if (response.status != HttpStatusCode.OK) {
            throw IOException("Bad response: $response")
        }

        val parameters = body.parseUrlEncodedParameters()
        require(parameters[HttpAuthHeader.Parameters.OAuthCallbackConfirmed] == "true") {
            "Response parameter oauth_callback_confirmed should be true"
        }

        return OAuthCallback.TokenPair(
            parameters[HttpAuthHeader.Parameters.OAuthToken]!!,
            parameters[HttpAuthHeader.Parameters.OAuthTokenSecret]!!
        )
    } catch (cause: Throwable) {
        throw IOException("Failed to acquire request token due to $body", cause)
    }
}

internal suspend fun ApplicationCall.redirectAuthenticateOAuth1a(
    settings: OAuthServerSettings.OAuth1aServerSettings,
    requestToken: OAuthCallback.TokenPair
) {
    redirectAuthenticateOAuth1a(settings.authorizeUrl, requestToken.token)
}

internal suspend fun ApplicationCall.redirectAuthenticateOAuth1a(authenticateUrl: String, requestToken: String) {
    val url = authenticateUrl.appendUrlParameters(
        "${HttpAuthHeader.Parameters.OAuthToken}=${requestToken.encodeURLParameter()}"
    )
    respondRedirect(url)
}

internal suspend fun requestOAuth1aAccessToken(
    client: HttpClient,
    settings: OAuthServerSettings.OAuth1aServerSettings,
    callbackResponse: OAuthCallback.TokenPair,
    nonce: String = generateNonce(),
    extraParameters: Map<String, String> = emptyMap()
): OAuthAccessTokenResponse.OAuth1a = requestOAuth1aAccessToken(
    client,
    settings.consumerSecret + "&", // TODO??
    settings.accessTokenUrl,
    settings.consumerKey,
    token = callbackResponse.token,
    verifier = callbackResponse.tokenSecret,
    nonce = nonce,
    extraParameters = extraParameters,
    accessTokenInterceptor = settings.accessTokenInterceptor
)

private suspend fun requestOAuth1aAccessToken(
    client: HttpClient,
    secretKey: String,
    baseUrl: String,
    consumerKey: String,
    token: String,
    verifier: String,
    nonce: String = generateNonce(),
    extraParameters: Map<String, String> = emptyMap(),
    accessTokenInterceptor: (HttpRequestBuilder.() -> Unit)?
): OAuthAccessTokenResponse.OAuth1a {
    val params = listOf(HttpAuthHeader.Parameters.OAuthVerifier to verifier) + extraParameters.toList()
    val authHeader = createUpgradeRequestTokenHeaderInternal(consumerKey, token, nonce)
        .signInternal(HttpMethod.Post, baseUrl, secretKey, params)

    // some of really existing OAuth servers don't support other accept header values so keep it
    val body = client.post(baseUrl) {
        header(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
        header(HttpHeaders.Accept, "*/*")
        // some of really existing OAuth servers don't support other accept header values so keep it

        setBody(
            WriterContent(
                { params.formUrlEncodeTo(this) },
                ContentType.Application.FormUrlEncoded
            )
        )

        accessTokenInterceptor?.invoke(this)
    }.body<String>()

    try {
        val parameters = body.parseUrlEncodedParameters()
        return OAuthAccessTokenResponse.OAuth1a(
            parameters[HttpAuthHeader.Parameters.OAuthToken] ?: throw OAuth1aException.MissingTokenException(),
            parameters[HttpAuthHeader.Parameters.OAuthTokenSecret] ?: throw OAuth1aException.MissingTokenException(),
            parameters
        )
    } catch (cause: OAuth1aException) {
        throw cause
    } catch (cause: Throwable) {
        throw IOException("Failed to acquire request token due to $body", cause)
    }
}

/**
 * Creates an HTTP auth header for OAuth1a obtain token request.
 */
private fun createObtainRequestTokenHeaderInternal(
    callback: String,
    consumerKey: String,
    nonce: String,
    timestamp: LocalDateTime = LocalDateTime.now()
): HttpAuthHeader.Parameterized = HttpAuthHeader.Parameterized(
    authScheme = AuthScheme.OAuth,
    parameters = mapOf(
        HttpAuthHeader.Parameters.OAuthCallback to callback,
        HttpAuthHeader.Parameters.OAuthConsumerKey to consumerKey,
        HttpAuthHeader.Parameters.OAuthNonce to nonce,
        HttpAuthHeader.Parameters.OAuthSignatureMethod to "HMAC-SHA1",
        HttpAuthHeader.Parameters.OAuthTimestamp to timestamp.toEpochSecond(ZoneOffset.UTC).toString(),
        HttpAuthHeader.Parameters.OAuthVersion to "1.0"
    )
)

/**
 * Creates an HTTP auth header for OAuth1a upgrade token request.
 */
private fun createUpgradeRequestTokenHeaderInternal(
    consumerKey: String,
    token: String,
    nonce: String,
    timestamp: LocalDateTime = LocalDateTime.now()
): HttpAuthHeader.Parameterized = HttpAuthHeader.Parameterized(
    authScheme = AuthScheme.OAuth,
    parameters = mapOf(
        HttpAuthHeader.Parameters.OAuthConsumerKey to consumerKey,
        HttpAuthHeader.Parameters.OAuthToken to token,
        HttpAuthHeader.Parameters.OAuthNonce to nonce,
        HttpAuthHeader.Parameters.OAuthSignatureMethod to "HMAC-SHA1",
        HttpAuthHeader.Parameters.OAuthTimestamp to timestamp.toEpochSecond(ZoneOffset.UTC).toString(),
        HttpAuthHeader.Parameters.OAuthVersion to "1.0"
    )
)

/**
 * Signs an HTTP auth header.
 */
private fun HttpAuthHeader.Parameterized.signInternal(
    method: HttpMethod,
    baseUrl: String,
    key: String,
    parameters: List<Pair<String, String>>
): HttpAuthHeader.Parameterized = withParameter(
    HttpAuthHeader.Parameters.OAuthSignature,
    signatureBaseStringInternal(this, method, baseUrl, parameters.toHeaderParamsList()).hmacSha1(key)
)

/**
 * Builds an OAuth1a signature base string as per RFC.
 */
internal fun signatureBaseStringInternal(
    header: HttpAuthHeader.Parameterized,
    method: HttpMethod,
    baseUrl: String,
    parameters: List<HeaderValueParam>
): String = listOf(
    method.value.toUpperCasePreservingASCIIRules(),
    baseUrl,
    parametersString(header.parameters + parameters)
).joinToString("&") { it.encodeURLParameter() }

private fun String.hmacSha1(key: String): String {
    val keySpec = SecretKeySpec(key.toByteArray(), "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(keySpec)

    return Base64.getEncoder().encodeToString(mac.doFinal(this.toByteArray()))
}

private fun parametersString(parameters: List<HeaderValueParam>): String =
    parameters.map { it.name.encodeURLParameter() to it.value.encodeURLParameter() }
        .sortedWith(compareBy<Pair<String, String>> { it.first }.then(compareBy { it.second }))
        .joinToString("&") { "${it.first}=${it.second}" }

/**
 * An OAuth1a server error.
 */
public sealed class OAuth1aException(message: String) : Exception(message) {

    /**
     * Thrown when an OAuth1a server didn't provide access token.
     */
    public class MissingTokenException : OAuth1aException("The OAuth1a server didn't provide access token")
}
