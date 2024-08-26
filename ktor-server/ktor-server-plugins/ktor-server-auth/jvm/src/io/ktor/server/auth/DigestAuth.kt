/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import java.security.*

/**
 * A `digest` [Authentication] provider.
 * @property realm specifies the value to be passed in the `WWW-Authenticate` header.
 * @property algorithmName a message digest algorithm to be used. Usually only `MD5` is supported by clients.
 */
public class DigestAuthenticationProvider internal constructor(
    config: Config
) : AuthenticationProvider(config) {

    private val realm: String = config.realm

    private val algorithmName: String = config.algorithmName

    private val nonceManager: NonceManager = config.nonceManager

    private val userNameRealmPasswordDigestProvider: DigestProviderFunction = config.digestProvider

    private val authenticationFunction: AuthenticationFunction<DigestCredential> = config.authenticationFunction

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val authorizationHeader = call.request.parseAuthorizationHeader()
        val credentials = authorizationHeader?.let { authHeader ->
            if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
                authHeader.toDigestCredential()
            } else {
                null
            }
        }

        val verify: suspend (DigestCredential) -> Boolean = {
            it.verifier(
                call.request.local.method,
                MessageDigest.getInstance(algorithmName),
                userNameRealmPasswordDigestProvider
            )
        }
        val principal = credentials?.let {
            if ((it.algorithm ?: "MD5") == algorithmName &&
                it.realm == realm &&
                nonceManager.verifyNonce(it.nonce) &&
                verify(it)
            ) {
                call.authenticationFunction(it)
            } else {
                null
            }
        }

        when (principal) {
            null -> {
                val cause = when (credentials) {
                    null -> AuthenticationFailedCause.NoCredentials
                    else -> AuthenticationFailedCause.InvalidCredentials
                }

                @Suppress("NAME_SHADOWING")
                context.challenge(digestAuthenticationChallengeKey, cause) { challenge, call ->
                    call.respond(
                        UnauthorizedResponse(
                            HttpAuthHeader.digestAuthChallenge(
                                realm,
                                algorithm = algorithmName,
                                nonce = nonceManager.newNonce()
                            )
                        )
                    )
                    challenge.complete()
                }
            }
            else -> context.principal(name, principal)
        }
    }

    /**
     * A configuration for the [digest] authentication provider.
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal var digestProvider: DigestProviderFunction = { userName, realm ->
            MessageDigest.getInstance(algorithmName).let { digester ->
                digester.reset()
                digester.update("$userName:$realm".toByteArray(Charsets.UTF_8))
                digester.digest()
            }
        }

        internal var authenticationFunction: AuthenticationFunction<DigestCredential> = { UserIdPrincipal(it.userName) }

        /**
         * Specifies a realm to be passed in the `WWW-Authenticate` header.
         */
        public var realm: String = "Ktor Server"

        /**
         * A message digest algorithm to be used. Usually only `MD5` is supported by clients.
         */
        public var algorithmName: String = "MD5"

        /**
         * [NonceManager] to be used to generate nonce values.
         */
        public var nonceManager: NonceManager = GenerateOnlyNonceManager

        /**
         * Sets a validation function that checks a specified [DigestCredential] instance and
         * returns principal [Any] in a case of successful authentication or null if authentication fails.
         */
        public fun validate(body: AuthenticationFunction<DigestCredential>) {
            authenticationFunction = body
        }

        /**
         * Configures a digest provider function that should fetch or compute message digest for the specified
         * `userName` and `realm`. A message digest is usually computed based on username, realm and password
         * concatenated with the colon character ':'. For example, `"$userName:$realm:$password"`.
         */
        public fun digestProvider(digest: DigestProviderFunction) {
            digestProvider = digest
        }
    }
}

/**
 * Provides a message digest for the specified username and realm or returns `null` if a user is missing.
 * This function could fetch digest from a database or compute it instead.
 */
public typealias DigestProviderFunction = suspend (userName: String, realm: String) -> ByteArray?

/**
 * Installs the digest [Authentication] provider.
 * To learn how to configure it, see [Digest authentication](https://ktor.io/docs/digest.html).
 */
public fun AuthenticationConfig.digest(
    name: String? = null,
    configure: DigestAuthenticationProvider.Config.() -> Unit
) {
    val provider = DigestAuthenticationProvider(DigestAuthenticationProvider.Config(name).apply(configure))
    register(provider)
}

/**
 * Digest credentials.
 * @see [digest]
 *
 * @property realm a digest authentication realm
 * @property userName
 * @property digestUri may be an absolute URI or `*`
 * @property nonce
 * @property opaque a string of data which should be returned by the client unchanged
 * @property nonceCount must be sent if [qop] is specified and must be `null` otherwise
 * @property algorithm a digest algorithm name
 * @property response consist of 32 hex digits (digested password and other fields as per RFC)
 * @property cnonce must be sent if [qop] is specified and must be `null` otherwise. Should be passed through unchanged.
 * @property qop a quality of protection sign
 */
public data class DigestCredential(
    val realm: String,
    val userName: String,
    val digestUri: String,
    val nonce: String,
    val opaque: String?,
    val nonceCount: String?,
    val algorithm: String?,
    val response: String,
    val cnonce: String?,
    val qop: String?
)

/**
 * Retrieves [DigestCredential] for this call.
 */
public fun ApplicationCall.digestAuthenticationCredentials(): DigestCredential? {
    return request.parseAuthorizationHeader()?.let { authHeader ->
        if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
            return authHeader.toDigestCredential()
        } else {
            null
        }
    }
}

private val digestAuthenticationChallengeKey: Any = "DigestAuth"

/**
 * Converts [HttpAuthHeader] to [DigestCredential].
 */
public fun HttpAuthHeader.Parameterized.toDigestCredential(): DigestCredential = DigestCredential(
    parameter("realm")!!,
    parameter("username")!!,
    parameter("uri")!!,
    parameter("nonce")!!,
    parameter("opaque"),
    parameter("nc"),
    parameter("algorithm"),
    parameter("response")!!,
    parameter("cnonce"),
    parameter("qop")
)

/**
 * Verifies that credentials are valid for a given [method], [digester], and [userNameRealmPasswordDigest].
 */
public suspend fun DigestCredential.verifier(
    method: HttpMethod,
    digester: MessageDigest,
    userNameRealmPasswordDigest: suspend (String, String) -> ByteArray?
): Boolean {
    val userNameRealmPasswordDigestResult = userNameRealmPasswordDigest(userName, realm)
    val validDigest = expectedDigest(method, digester, userNameRealmPasswordDigestResult ?: ByteArray(0))

    val incoming: ByteArray = try {
        hex(response)
    } catch (e: NumberFormatException) {
        return false
    }

    // here we do null-check in the end because it should be always time-constant comparison due to security reasons
    return MessageDigest.isEqual(incoming, validDigest) && userNameRealmPasswordDigestResult != null
}

/**
 * Calculates the expected digest bytes for this [DigestCredential].
 */
public fun DigestCredential.expectedDigest(
    method: HttpMethod,
    digester: MessageDigest,
    userNameRealmPasswordDigest: ByteArray
): ByteArray {
    fun digest(data: String): ByteArray {
        digester.reset()
        digester.update(data.toByteArray(Charsets.ISO_8859_1))
        return digester.digest()
    }

    // H(A1) in the RFC
    val start = hex(userNameRealmPasswordDigest)

    // H(A2) in the RFC
    val end = hex(digest("${method.value.toUpperCasePreservingASCIIRules()}:$digestUri"))

    val hashParameters = when (qop) {
        null -> listOf(start, nonce, end)
        else -> listOf(
            start,
            nonce,
            nonceCount,
            cnonce,
            qop,
            end
        )
    }.joinToString(":")

    return digest(hashParameters)
}
