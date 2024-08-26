/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.impl.*
import com.auth0.jwt.interfaces.*
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import java.security.interfaces.*
import java.util.*

internal fun Jwk.makeAlgorithm(): Algorithm = when (algorithm) {
    "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
    "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
    "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
    "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
    "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
    null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
}

internal fun AuthenticationContext.bearerChallenge(
    cause: AuthenticationFailedCause,
    realm: String,
    schemes: JWTAuthSchemes,
    challengeFunction: JWTAuthChallengeFunction
) {
    challenge(JWTAuthKey, cause) { challenge, call ->
        challengeFunction(JWTChallengeContext(call), schemes.defaultScheme, realm)
        if (!challenge.completed && call.response.status() != null) {
            challenge.complete()
        }
    }
}

internal fun getVerifier(
    jwkProvider: JwkProvider,
    issuer: String?,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes,
    jwtConfigure: Verification.() -> Unit
): JWTVerifier? {
    val jwk = token.getBlob(schemes)?.let { blob ->
        try {
            jwkProvider.get(JWT.decode(blob).keyId)
        } catch (cause: JwkException) {
            JWTLogger.trace("Failed to get JWK", cause)
            null
        } catch (cause: JWTDecodeException) {
            JWTLogger.trace("Illegal JWT", cause)
            null
        }
    } ?: return null

    val algorithm = try {
        jwk.makeAlgorithm()
    } catch (cause: Throwable) {
        JWTLogger.trace("Failed to create algorithm {}: {}", jwk.algorithm, cause.message ?: cause.javaClass.simpleName)
        return null
    }

    return when (issuer) {
        null -> JWT.require(algorithm)
        else -> JWT.require(algorithm).withIssuer(issuer)
    }.apply(jwtConfigure).build()
}

internal fun getVerifier(
    jwkProvider: JwkProvider,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes,
    configure: JWTConfigureFunction
): JWTVerifier? {
    return getVerifier(jwkProvider, null, token, schemes, configure)
}

internal suspend fun verifyAndValidate(
    call: ApplicationCall,
    jwtVerifier: JWTVerifier?,
    token: HttpAuthHeader,
    schemes: JWTAuthSchemes,
    validate: suspend ApplicationCall.(JWTCredential) -> Any?
): Any? {
    val jwt = try {
        token.getBlob(schemes)?.let { jwtVerifier?.verify(it) }
    } catch (cause: JWTVerificationException) {
        JWTLogger.trace("Token verification failed", cause)
        null
    } ?: return null

    val payload = jwt.parsePayload()
    val credentials = JWTCredential(payload)
    return validate(call, credentials)
}

internal fun HttpAuthHeader.getBlob(schemes: JWTAuthSchemes) = when {
    this is HttpAuthHeader.Single && authScheme in schemes -> blob
    else -> null
}

internal fun ApplicationRequest.parseAuthorizationHeaderOrNull() = try {
    parseAuthorizationHeader()
} catch (cause: IllegalArgumentException) {
    JWTLogger.trace("Illegal HTTP auth header", cause)
    null
}

internal fun DecodedJWT.parsePayload(): Payload {
    val payloadString = String(Base64.getUrlDecoder().decode(payload))
    return JWTParser().parsePayload(payloadString)
}
