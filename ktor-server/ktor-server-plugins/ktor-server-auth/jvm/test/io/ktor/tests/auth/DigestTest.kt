/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.security.*
import kotlin.test.*

class DigestTest {
    @Test
    fun createExampleChallengeFromRFC() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Plugins) {
                call.respond(
                    UnauthorizedResponse(
                        HttpAuthHeader.digestAuthChallenge(
                            realm = "testrealm@host.com",
                            nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093",
                            opaque = "5ccc069c403ebaf9f0171e9517f40e41"
                        )
                    )
                )
            }

            val response = handleRequest {
            }

            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
            assertEquals(
                """Digest
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41",
                 algorithm="MD5" """.normalize(),
                response.response.headers[HttpHeaders.WWWAuthenticate]
            )
        }
    }

    @Test
    fun testParseDigestExampleFromRFC() {
        withTestApplication {
            val foundDigests = arrayListOf<DigestCredential>()

            application.install(Authentication) {
                provider {
                    authenticate { context ->
                        context.call.digestAuthenticationCredentials()?.let { digest -> foundDigests.add(digest) }
                    }
                }
            }

            application.routing {
                authenticate {
                    get("/") {}
                }
            }

            handleRequest {
                uri = "/"

                addHeader(
                    HttpHeaders.Authorization,
                    """Digest username="Mufasa",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="6629fae49393a05397450978507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()
                )
            }

            assertEquals(1, foundDigests.size)

            val theOnlyDigest = foundDigests.single()

            assertEquals("testrealm@host.com", theOnlyDigest.realm)
            assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", theOnlyDigest.nonce)
            assertEquals("/dir/index.html", theOnlyDigest.digestUri)
            assertEquals("00000001", theOnlyDigest.nonceCount)
            assertEquals("6629fae49393a05397450978507c4ef1", theOnlyDigest.response)
            assertEquals("5ccc069c403ebaf9f0171e9517f40e41", theOnlyDigest.opaque)
        }
    }

    @Test
    fun testVerify(): Unit = runBlocking {
        val authHeaderContent = """Digest username="Mufasa",
                     realm="testrealm@host.com",
                     nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                     uri="/dir/index.html",
                     qop=auth,
                     nc=00000001,
                     cnonce="0a4f113b",
                     response="6629fae49393a05397450978507c4ef1",
                     opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()

        val authHeader = parseAuthorizationHeader(authHeaderContent) as HttpAuthHeader.Parameterized
        val digest = authHeader.toDigestCredential()

        val p = "Circle Of Life"
        val userNameRealmPassword = "${digest.userName}:${digest.realm}:$p"
        val digester = MessageDigest.getInstance(digest.algorithm ?: "MD5")

        assertEquals(
            digest.response,
            hex(digest.expectedDigest(HttpMethod.Get, digester, digest(digester, userNameRealmPassword)))
        )
        assertTrue(digest.verifier(HttpMethod.Get, digester) { user, realm -> digest(digester, "$user:$realm:$p") })
    }

    @Test
    fun testValidateFunction() = testApplication {
        class TestPrincipal(val name: String)
        install(Authentication) {
            digest {
                val p = "Circle Of Life"
                realm = "testrealm@host.com"
                this.nonceManager = GenerateOnlyNonceManager
                digestProvider { userName, realm -> digest(MessageDigest.getInstance("MD5"), "$userName:$realm:$p") }
                validate {
                    if (it.userName == "admin") TestPrincipal(it.userName) else null
                }
            }
        }
        routing {
            authenticate {
                get("/") {
                    call.respondText("OK")
                }
            }
        }
        val responseWrongAuth = client.get("/") {
            header(
                HttpHeaders.Authorization,
                """Digest username="Mufasa",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="6629fae49393a05397450978507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, responseWrongAuth.status)

        val responseCorrectAuth = client.get("/") {
            header(
                HttpHeaders.Authorization,
                """Digest username="admin",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="b9b12c2f6abe2d166e5743ed1e687ed6",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()
            )
        }
        assertEquals(HttpStatusCode.OK, responseCorrectAuth.status)
    }

    private fun Application.configureDigestServer(nonceManager: NonceManager = GenerateOnlyNonceManager) {
        install(Authentication) {
            digest {
                val p = "Circle Of Life"
                realm = "testrealm@host.com"
                this.nonceManager = nonceManager
                digestProvider { userName, realm ->
                    when (userName) {
                        "missing" -> null
                        else -> digest(MessageDigest.getInstance("MD5"), "$userName:$realm:$p")
                    }
                }
            }
        }

        routing {
            authenticate {
                get("/") { call.respondText("Secret info") }
            }
        }
    }

    @Test
    fun testDigestFromRFCExample() {
        withTestApplication {
            application.configureDigestServer()

            val response = handleRequest {
                uri = "/"

                addHeader(
                    HttpHeaders.Authorization,
                    """Digest username="Mufasa",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="6629fae49393a05397450978507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()
                )
            }

            assertEquals(HttpStatusCode.OK, response.response.status())
            assertEquals("Secret info", response.response.content)
        }
    }

    @Test
    fun testDigestFromRFCExampleAuthFailedDueToWrongRealm() {
        withTestApplication {
            application.configureDigestServer()

            val response = handleRequest {
                uri = "/"

                addHeader(
                    HttpHeaders.Authorization,
                    """Digest username="Mufasa",
                 realm="testrealm@host.com1",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="6629fae49393a05397450978507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()
                )
            }

            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
        }
    }

    @Test
    fun testBadRequestOnInvalidHeader() {
        withTestApplication {
            application.configureDigestServer()

            val call = handleRequest { addHeader(HttpHeaders.Authorization, "D<gest code") }

            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    fun testDigestFromRFCExampleAuthFailed() {
        withTestApplication {
            application.configureDigestServer()

            val response = handleRequest {
                uri = "/"

                addHeader(
                    HttpHeaders.Authorization,
                    """Digest username="Mufasa",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="bad response goes here  507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()
                )
            }

            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
        }
    }

    @Test
    fun testDigestFromRFCExampleAuthFailedDueToMissingUser() {
        withTestApplication {
            application.configureDigestServer()

            val response = handleRequest {
                uri = "/"

                addHeader(
                    HttpHeaders.Authorization,
                    """Digest username="missing",
                 realm="testrealm@host.com",
                 nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
                 uri="/dir/index.html",
                 qop=auth,
                 nc=00000001,
                 cnonce="0a4f113b",
                 response="6629fae49393a05397450978507c4ef1",
                 opaque="5ccc069c403ebaf9f0171e9517f40e41"""".normalize()
                )
            }

            assertEquals(HttpStatusCode.Unauthorized, response.response.status())
        }
    }

    @Test
    fun testDigestFromRFCExampleAuthFailedDueToWrongNonce() {
        val key = "test".toByteArray()
        val nonceValue = "test-nonce"

        withTestApplication {
            application.configureDigestServer(
                nonceManager = StatelessHmacNonceManager(
                    key,
                    nonceGenerator = { nonceValue }
                )
            )

            val challenge = handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.Unauthorized, call.response.status())
                parseAuthorizationHeader(call.response.headers[HttpHeaders.WWWAuthenticate]!!)
            }

            val nonce = (challenge as? HttpAuthHeader.Parameterized)?.parameter("nonce")

            assertNotNull(challenge, "Challenge is missing")
            assertNotNull(nonce, "Nonce is missing")

            val authHeader = HttpAuthHeader.Parameterized(
                AuthScheme.Digest,
                linkedMapOf(
                    "username" to "Mufasa",
                    "realm" to "testrealm@host.com",
                    "nonce" to nonce,
                    "uri" to "/dir/index.html",
                    "qop" to "auth",
                    "nc" to "00000001",
                    "cnonce" to "0a4f113b",
                    "response" to "unknown yet",
                    "opaque" to "5ccc069c403ebaf9f0171e9517f40e41"
                ),
                HeaderValueEncoding.QUOTED_ALWAYS
            )

            val userRealmPassDigest =
                digest(MessageDigest.getInstance("MD5"), "Mufasa:testrealm@host.com:Circle Of Life")

            val expectedDigest = authHeader.toDigestCredential().expectedDigest(
                HttpMethod.Get,
                MessageDigest.getInstance("MD5"),
                userRealmPassDigest
            )

            handleRequest {
                uri = "/"

                addHeader(
                    HttpHeaders.Authorization,
                    authHeader.withReplacedParameter("response", hex(expectedDigest)).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }

            handleRequest {
                uri = "/"

                addHeader(
                    HttpHeaders.Authorization,
                    authHeader.withReplacedParameter("response", hex(expectedDigest)).withReplacedParameter(
                        "nonce",
                        flipLastHexDigit(nonce)
                    ).render()
                )
            }.let { call ->
                assertEquals(HttpStatusCode.Unauthorized, call.response.status())
            }
        }
    }

    private fun flipLastHexDigit(sessionId: String) = sessionId.mapIndexed { index, letter ->
        when {
            index != sessionId.lastIndex -> letter
            letter == '0' -> '1'
            else -> '0'
        }
    }.joinToString("")

    private fun digest(digester: MessageDigest, data: String): ByteArray {
        digester.reset()
        digester.update(data.toByteArray(Charsets.ISO_8859_1))
        return digester.digest()
    }

    private fun String.normalize() = lineSequence().map { it.trim() }.joinToString(" ")
}
