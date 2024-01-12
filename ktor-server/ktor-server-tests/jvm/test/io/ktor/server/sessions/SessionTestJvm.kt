/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.random.*
import kotlin.test.*

@Suppress("ReplaceSingleLineLet", "DEPRECATION")
class SessionTestJvm {
    private val cookieName = "_S" + Random.nextInt(100)

    @Test
    fun testSessionByValueMac() {
        val key = hex("03515606058610610561058")
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    transform(SessionTransportTransformerMessageAuthentication(key))
                }
            }

            commonSignedChecks()
        }
    }

    @Test
    fun testSessionEncrypted() {
        val encryptKey = hex("00112233445566778899aabbccddeeff")
        val signKey = hex("02030405060708090a0b0c")
        val forcedIvForTesting = hex("00112233445566778899aabbccddeeff")

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    transform(SessionTransportTransformerEncrypt(encryptKey, signKey, { forcedIvForTesting }))
                }
            }

            application.routing {
                get("/3") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/4") {
                    call.respondText("ok:" + call.sessions.get<TestUserSession>()?.userId)
                }
            }

            handleRequest(HttpMethod.Get, "/3").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertEquals(
                    "00112233445566778899aabbccddeeff/" +
                        "9b8cf9900ff0f63849cb7a2ad71ed8a1ac9f0e2735492d2a5129cf278d70ab27:" +
                        "40af9f393a00034b53d2267251047259005bdf5ff104eede599149e80e6fee13",
                    sessionCookie!!.value
                )
            }

            handleRequest(HttpMethod.Get, "/4") {
                addHeader(HttpHeaders.Cookie, "$cookieName=INVALID")
            }.let { call ->
                assertEquals("ok:null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/4") {
                addHeader(HttpHeaders.Cookie, "$cookieName=abc/abc:abc")
            }.let { call ->
                assertEquals("ok:null", call.response.content)
            }

            commonSignedChecks()
        }
    }

    @Test
    fun testSessionEncryptedBackwardCompatible() {
        val encryptKey = hex("00112233445566778899aabbccddeeff")
        val signKey = hex("02030405060708090a0b0c")
        val forcedIvForTesting = hex("00112233445566778899aabbccddeeff")

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    serializer = reflectionSessionSerializer()
                    transform(
                        SessionTransportTransformerEncrypt(
                            encryptKey,
                            signKey,
                            { forcedIvForTesting },
                            backwardCompatibleRead = true
                        )
                    )
                }
            }

            application.routing {
                get("/3") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/4") {
                    call.respondText("ok:" + call.sessions.get<TestUserSession>()?.userId)
                }
            }

            handleRequest(HttpMethod.Get, "/3").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertEquals(
                    "00112233445566778899aabbccddeeff/" +
                        "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                        "65f0bd15bfa7e96b8bd9e134e23a24860b324e361c606e6800af050f5d104b68",
                    sessionCookie!!.value
                )
            }

            handleRequest(HttpMethod.Get, "/4") {
                addHeader(
                    HttpHeaders.Cookie,
                    "$cookieName=" + "00112233445566778899aabbccddeeff/" +
                        "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                        "51a5e9fcd1c91418f9a623bafa5022a524348e44244265dc0cab2cebacc28a5d"
                )
            }.let { call ->
                assertEquals("ok:id2", call.response.content)
            }

            commonSignedChecks()
        }
    }

    @Test
    fun testSessionEncryptedNotBackwardCompatible() {
        val encryptKey = hex("00112233445566778899aabbccddeeff")
        val signKey = hex("02030405060708090a0b0c")
        val forcedIvForTesting = hex("00112233445566778899aabbccddeeff")

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    serializer = reflectionSessionSerializer()
                    transform(
                        SessionTransportTransformerEncrypt(
                            encryptKey,
                            signKey,
                            { forcedIvForTesting },
                        )
                    )
                }
            }

            application.routing {
                get("/3") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/4") {
                    call.respondText("ok:" + call.sessions.get<TestUserSession>()?.userId)
                }
            }

            handleRequest(HttpMethod.Get, "/3").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertEquals(
                    "00112233445566778899aabbccddeeff/" +
                        "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                        "65f0bd15bfa7e96b8bd9e134e23a24860b324e361c606e6800af050f5d104b68",
                    sessionCookie!!.value
                )
            }

            handleRequest(HttpMethod.Get, "/4") {
                addHeader(
                    HttpHeaders.Cookie,
                    "$cookieName=" + "00112233445566778899aabbccddeeff/" +
                        "c3850fc1ddc62f71ec5eaad6d393b91fa809fe32a1cf0cb4730788c5a489daef:" +
                        "51a5e9fcd1c91418f9a623bafa5022a524348e44244265dc0cab2cebacc28a5d"
                )
            }.let { call ->
                assertEquals("ok:null", call.response.content)
            }

            commonSignedChecks()
        }
    }

    private fun TestApplicationEngine.commonSignedChecks() {
        application.routing {
            get("/1") {
                call.sessions.set(TestUserSession("id 2", emptyList()))
                call.respondText("ok")
            }
            get("/2") {
                call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
            }
        }

        var sessionId: String
        var sessionHeader: String
        handleRequest(HttpMethod.Get, "/1").let { call ->
            val sessionCookie = call.response.cookies[cookieName]
            assertNotNull(sessionCookie, "No session cookie found")
            sessionId = sessionCookie.value
            sessionHeader = call.response.headers[HttpHeaders.SetCookie]!!
        }

        handleRequest(HttpMethod.Get, "/2") {
            addHeader(HttpHeaders.Cookie, "$cookieName=${sessionId.encodeURLParameter()}")
        }.let { call ->
            assertEquals("ok, id 2", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/2") {
            addHeader(HttpHeaders.Cookie, sessionHeader)
        }.let { call ->
            assertEquals("ok, id 2", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/2").let { call ->
            assertEquals("ok, null", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/2") {
            val brokenSession = flipLastHexDigit(sessionId)
            addHeader(HttpHeaders.Cookie, "$cookieName=${brokenSession.encodeURLParameter()}")
        }.let { call ->
            assertEquals("ok, null", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/2") {
            val invalidHex = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
            addHeader(HttpHeaders.Cookie, "$cookieName=${invalidHex.encodeURLParameter()}")
        }.let { call ->
            assertEquals("ok, null", call.response.content)
        }
    }

    private fun flipLastHexDigit(sessionId: String) = sessionId.mapIndexed { index, letter ->
        when {
            index != sessionId.lastIndex -> letter
            letter == '0' -> '1'
            else -> '0'
        }
    }.joinToString("")
}
