/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.serialization.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.random.*
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Suppress("ReplaceSingleLineLet")
class SessionTest {
    private val cookieName = "_S" + Random.nextInt(100)

    private val HttpMessage.cookies: Map<String, Cookie> get() = setCookie().associateBy { it.name }

    @Test
    fun testSessionCreateDelete() = testApplication {
        install(Sessions) {
            cookie<TestUserSession>(cookieName)
        }

        routing {
            get("/0") {
                call.sessions.set(TestUserSession("a", emptyList()))
                call.sessions.clear<TestUserSession>()
                call.respondText("No session")
            }
        }
        println(client.get("/0"))
        assertNull(
            client.get("/0").cookies[cookieName],
            "There should be no session data after setting and clearing"
        )
    }

    @Test
    fun testSessionByValue() = testApplication {
        install(Sessions) {
            cookie<TestUserSession>(cookieName) {
                cookie.domain = "foo.bar"
                cookie.maxAge = 1.hours
            }
        }

        routing {
            get("/0") {
                assertNull(call.sessions.get<TestUserSession>())
                call.respondText("No session")
            }
            get("/1") {
                var session: TestUserSession? = call.sessions.get()
                assertNull(session)

                assertFailsWith(IllegalArgumentException::class) {
                    call.sessions.set(EmptySession()) // bad class
                }

                call.sessions.set(TestUserSession("id1", emptyList()))
                session = call.sessions.get()
                assertNotNull(session)

                call.respondText("ok")
            }
            get("/2") {
                assertEquals(TestUserSession("id1", emptyList()), call.sessions.get())

                call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
            }
        }

        assertNull(
            client.get("/0").cookies[cookieName],
            "There should be no session set by default"
        )

        var sessionParam: String
        client.get("/1").let { call ->
            val sessionCookie = call.cookies[cookieName]
            assertNotNull(sessionCookie, "No session cookie found")
            sessionParam = sessionCookie.value
            assertEquals("foo.bar", sessionCookie.domain)
            assertEquals(3600, sessionCookie.maxAge)
            assertNotNull(sessionCookie.expires)

            assertEquals(
                TestUserSession("id1", emptyList()),
                defaultSessionSerializer<TestUserSession>().deserialize(sessionParam)
            )
        }

        client.get("/2") {
            header(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLParameter()}")
        }.let { call ->
            assertEquals("ok, id1", call.bodyAsText())
        }
    }

    @Test
    fun testSessionWithEncodedCookie() = testApplication {
        install(Sessions) {
            cookie<TestUserSession>(cookieName, SessionStorageMemory()) {
                cookie.encoding = CookieEncoding.BASE64_ENCODING
            }
        }
        routing {
            get("/1") {
                var session: TestUserSession? = call.sessions.get()
                assertNull(session)

                session = TestUserSession("id1", emptyList())
                call.sessions.set(session)

                session = call.sessions.get()
                assertNotNull(session)

                call.respond("Ok")
            }
            get("/2") {
                val session = call.sessions.get<TestUserSession>()
                assertNotNull(session)
                call.respond(session.userId)
            }
        }
        var sessionParam: String
        client.get("/1").let { call ->
            val sessionCookie = call.cookies[cookieName]

            assertNotNull(sessionCookie, "No session cookie found")
            assertEquals(CookieEncoding.BASE64_ENCODING, sessionCookie.encoding)

            sessionParam = sessionCookie.value.encodeBase64()
        }

        client.get("/2") {
            header(HttpHeaders.Cookie, "$cookieName=$sessionParam")
        }.let { call ->
            assertEquals("id1", call.bodyAsText())
        }
    }

    @Test
    fun testRoutes() = testApplication {
        install(Sessions) {
            cookie<TestUserSession>(cookieName)
        }
        routing {
            route("/") {
                get("/0") {
                    assertNull(call.sessions.get<TestUserSession>())
                    call.respondText("No session")
                }
                get("/1") {
                    var session: TestUserSession? = call.sessions.get()
                    assertNull(session)

                    assertFailsWith(IllegalArgumentException::class) {
                        call.sessions.set(EmptySession()) // bad class
                    }

                    call.sessions.set(TestUserSession("id1", emptyList()))
                    session = call.sessions.get()
                    assertNotNull(session)

                    call.respondText("ok")
                }
                get("/2") {
                    assertEquals(TestUserSession("id1", emptyList()), call.sessions.get())

                    call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                }
            }
        }

        client.get("/0").let { call ->
            assertNull(call.cookies[cookieName], "There should be no session set by default")
            assertEquals("No session", call.bodyAsText())
        }

        var sessionParam: String
        client.get("/1").let { call ->
            val sessionCookie = call.cookies[cookieName]
            assertNotNull(sessionCookie, "No session cookie found")
            sessionParam = sessionCookie.value

            assertEquals(
                TestUserSession("id1", emptyList()),
                defaultSessionSerializer<TestUserSession>().deserialize(sessionParam)
            )
            assertEquals("ok", call.bodyAsText())
        }
        client.get("/2") {
            header(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLParameter()}")
        }.let { call ->
            assertEquals("ok, id1", call.bodyAsText())
        }
    }

    @Test
    fun testRoutesIsolation() = testApplication {
        val sessionA = TestUserSession("id1", listOf("a"))
        val sessionB = TestUserSessionB("id2", listOf("b"))
        routing {
            route("/a") {
                install(Sessions) {
                    cookie<TestUserSession>(cookieName)
                }

                get("/1") {
                    call.sessions.set(sessionA)
                    call.respondText("ok")
                }
                get("/2") {
                    assertEquals(sessionA, call.sessions.get())
                    call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                }
            }

            route("/b") {
                install(Sessions) {
                    cookie<TestUserSessionB>(cookieName)
                }
                get("/1") {
                    call.sessions.set(sessionB)
                    call.respondText("ok")
                }
                get("/2") {
                    assertEquals(sessionB, call.sessions.get())
                    call.respondText("ok, ${call.sessions.get<TestUserSessionB>()?.userId}")
                }
            }
        }

        var sessionParam: String
        client.get("/a/1").let { call ->
            val sessionCookie = call.cookies[cookieName]
            assertNotNull(sessionCookie, "No session cookie found")
            sessionParam = sessionCookie.value

            assertEquals(sessionA, defaultSessionSerializer<TestUserSession>().deserialize(sessionParam))
            assertEquals("ok", call.bodyAsText())
        }
        client.get("/a/2") {
            header(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLParameter()}")
        }.let { call ->
            assertEquals("ok, id1", call.bodyAsText())
        }

        client.get("/b/1").let { call ->
            val sessionCookie = call.cookies[cookieName]
            assertNotNull(sessionCookie, "No session cookie found")
            sessionParam = sessionCookie.value

            assertEquals(sessionB, defaultSessionSerializer<TestUserSessionB>().deserialize(sessionParam))
            assertEquals("ok", call.bodyAsText())
        }
        client.get("/b/2") {
            header(HttpHeaders.Cookie, "$cookieName=${sessionParam.encodeURLParameter()}")
        }.let { call ->
            assertEquals("ok, id2", call.bodyAsText())
        }
    }

    @Test
    fun testSessionById() {
        val sessionStorage = SessionStorageMemory()

        testApplication {
            install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage)
            }

            routing {
                get("/0") {
                    call.respondText("There should be no session started")
                }
                get("/1") {
                    call.sessions.set(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                }
                get("/2") {
                    val session = call.sessions.get<TestUserSession>()
                    assertEquals("id2", session?.userId)
                    assertEquals(listOf("item1"), session?.cart)

                    call.respondText("ok")
                }
                get("/3") {
                    call.respondText(call.sessions.get<TestUserSession>()?.userId ?: "no session")
                }
            }

            assertNull(
                client.get("/0").cookies[cookieName],
                "There should be no session set by default"
            )

            var sessionId: String
            client.get("/1").let { response ->
                val sessionCookie = response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session id cookie found")
                sessionId = sessionCookie.value
                assertTrue { sessionId.matches("[A-Za-z0-9]+".toRegex()) }
            }
            val serializedSession = sessionStorage.read(sessionId)
            assertNotNull(serializedSession)
            assertEquals("id2", defaultSessionSerializer<TestUserSession>().deserialize(serializedSession).userId)

            client.get("/2") {
                header(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }

            client.get("/3") {
                header(HttpHeaders.Cookie, "$cookieName=bad$sessionId")
            }.let { call ->
                assertEquals("no session", call.bodyAsText())
            }
        }
    }

    @Test
    fun testSessionByIdAccessors() {
        val sessionStorage = SessionStorageMemory()

        testApplication {
            install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage)
            }

            routing {
                get("/0") {
                    assertNull(call.sessionId, "There should be no session set by default")
                    assertNull(call.sessionId<TestUserSession>(), "There should be no session set by default")
                    assertFails {
                        call.sessionId<EmptySession>()
                    }
                    call.respondText("There should be no session started")
                }
            }

            assertEquals("There should be no session started", client.get("/0").bodyAsText())
        }
    }

    @Test
    fun testSessionByIdServer() {
        val sessionStorage = SessionStorageMemory()
        testApplication {
            install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage)
            }
            var serverSessionId = "_invalid"
            routing {
                get("/0") {
                    assertNull(call.sessionId, "There should be no session set by default")
                    assertNull(call.sessionId<TestUserSession>(), "There should be no session set by default")
                    call.respondText("There should be no session started")
                }
                get("/1") {
                    call.sessions.set(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                    serverSessionId = call.sessionId ?: error("No session id found.")
                    assertTrue { serverSessionId.matches("[A-Za-z0-9]+".toRegex()) }
                }
            }

            assertNull(
                client.get("/0").cookies[cookieName],
                "There should be no session set by default"
            )

            client.get("/1").let { response ->
                val sessionCookie = response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session id cookie found")
                val clientSessionId = sessionCookie.value
                assertEquals(serverSessionId, clientSessionId)
            }

            val serializedSession = sessionStorage.read(serverSessionId)
            assertNotNull(serializedSession)
            assertEquals(
                "id2",
                defaultSessionSerializer<TestUserSession>().deserialize(serializedSession).userId
            )
        }
    }

    @Test
    fun testSessionByIdServerWithBackwardCompatibleSerialization() {
        val sessionStorage = SessionStorageMemory()
        testApplication {
            install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage) {
                    serializer = KotlinxBackwardCompatibleSessionSerializer()
                }
            }
            var serverSessionId = "_invalid"
            routing {
                get("/0") {
                    assertNull(call.sessionId, "There should be no session set by default")
                    assertNull(
                        call.sessionId<TestUserSession>(),
                        "There should be no session set by default"
                    )
                    call.respondText("There should be no session started")
                }
                get("/1") {
                    call.sessions.set(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                    serverSessionId = call.sessionId ?: error("No session id found.")
                    assertTrue { serverSessionId.matches("[A-Za-z0-9]+".toRegex()) }
                }
            }

            assertNull(
                client.get("/0").cookies[cookieName],
                "There should be no session set by default"
            )

            client.get("/1").let { response ->
                val sessionCookie = response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session id cookie found")
                val clientSessionId = sessionCookie.value
                assertEquals(serverSessionId, clientSessionId)
            }

            val serializedSession = sessionStorage.read(serverSessionId)
            assertNotNull(serializedSession)
            assertEquals(
                "id2",
                KotlinxBackwardCompatibleSessionSerializer<TestUserSession>().deserialize(serializedSession).userId
            )
        }
    }

    @Test
    fun testSessionByIdCookie() {
        val sessionStorage = SessionStorageMemory()
        var id = 777
        val durationSeconds = 5L

        testApplication {
            install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage) {
                    cookie.maxAge = durationSeconds.seconds
                    identity { (id++).toString() }
                }
            }

            routing {
                get("/1") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
            }

            fun GMTDate.plusAndDiscardMillis() =
                (this + durationSeconds * 1000L).toHttpDate().fromHttpToGmtDate()

            val before = GMTDate()
            client.get("/1").let { call ->
                val sessionCookie = call.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                val after = GMTDate()

                assertEquals(durationSeconds, sessionCookie.maxAge?.toLong())
                assertEquals("777", sessionCookie.value)
                assertNotNull(sessionCookie.expires, "Expires cookie value is not set")
                assertTrue("Expires cookie parameter value should be in the specified dates range") {
                    sessionCookie.expires!! in before.plusAndDiscardMillis()..after.plusAndDiscardMillis()
                }
            }
        }
    }

    @Test
    fun testSessionByInvalidId() = testApplication {
        install(Sessions) {
            cookie<TestUserSession>(cookieName, SessionStorageMemory())
        }

        routing {
            get("/1") {
                call.sessions.set(TestUserSession("id2", listOf("item1")))
                call.respondText("ok")
            }
            get("/2") {
                val session = call.sessions.get<TestUserSession>()
                call.respondText(session?.userId ?: "none")
            }
        }

        val call = client.get("/1") {
            header(HttpHeaders.Cookie, "$cookieName=invalid")
        }
        val sessionId = call.cookies[cookieName]!!.value

        val nextCall = client.get("/1") {
            header(HttpHeaders.Cookie, "$cookieName=$sessionId")
        }
        assertEquals(sessionId, nextCall.cookies[cookieName]!!.value)

        client.get("/2") {
            header(HttpHeaders.Cookie, "$cookieName=invalid2")
        }.let { call2 ->
            // we are sending expired cookie to remove outdated/invalid session id
            assertEquals("none", call2.bodyAsText())
            call2.cookies[cookieName].let { cookie ->
                assertNotNull(cookie, "cookie should be resend (expired)")
                assertEquals(0, cookie.maxAge)
                assertNotNull(cookie.expires)
                assertEquals(1970, cookie.expires!!.year)
            }
        }
    }

    @Test
    fun testHttpSessionCookie() = testApplication {
        // test session cookie in terms of HTTP
        // that should be discarded on client exit

        install(Sessions) {
            cookie<TestUserSession>(cookieName, SessionStorageMemory()) {
                cookie.maxAge = null
            }
        }

        routing {
            get("/set-cookie") {
                call.sessions.set(TestUserSession("id2", listOf("item1")))
                call.respondText("ok")
            }
        }

        client.get("/set-cookie").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            val parsedCookies = call.cookies[cookieName]!!
            assertNull(parsedCookies.expires)
            assertEquals(null, parsedCookies.maxAge)
        }
    }

    @Test
    fun settingSessionAfterResponseTest() = testApplication {
        install(Sessions) {
            cookie<TestUserSession>(cookieName)
        }

        routing {
            get("/after-response") {
                call.respondText("OK")
                call.sessions.set(TestUserSession("id", emptyList()))
            }
        }

        assertFailsWith<TooLateSessionSetException> {
            client.get("/after-response").bodyAsText()
        }
    }

    @Test
    fun testSessionLongDuration() = testApplication {
        val transport = SessionTransportCookie(
            "test",
            CookieConfiguration().apply {
                maxAge = (365 * 100).days
            },
            emptyList()
        )

        application {
            val call = TestApplicationCall(this, coroutineContext = Dispatchers.Default)
            transport.send(call, "my-session")
            val cookies = call.response.cookies["test"]
            assertNotNull(cookies)
            assertEquals(Int.MAX_VALUE, cookies.maxAge)
        }
    }

    @Test
    fun testSessionOverflowDuration() = testApplication {
        val transport = SessionTransportCookie(
            "test",
            CookieConfiguration().apply {
                maxAge = Long.MAX_VALUE.seconds
            },
            emptyList()
        )

        application {
            val call = TestApplicationCall(this, coroutineContext = Dispatchers.Default)
            transport.send(call, "my-session")

            val cookies = call.response.cookies["test"]
            assertNotNull(cookies)
            assertEquals(Int.MAX_VALUE, cookies.maxAge)
        }
    }

    @Test
    fun testDuplicateProvidersDiagnostics() = testApplication {
        install(Sessions) {
            cookie<TestUserSession>("name1")

            assertFails("Registering the same provider twice should be prohibited") {
                cookie<TestUserSession>("name1")
            }

            assertFails("Registering provider with the same name should be prohibited") {
                cookie<TestUserSessionB>("name1")
            }

            assertFails("Registering provider with the same type should be prohibited") {
                cookie<TestUserSession>("name2")
            }

            on("Registering another provider should be allowed") {
                cookie<TestUserSessionB>("name2")
            }
        }
    }

    @Test
    fun testMissingSessionsPlugin() = testApplication {
        routing {
            get("/") {
                val cause = assertFailsWith<MissingApplicationPluginException> {
                    call.sessions.get<EmptySession>()
                }
                call.respondText(cause.key.name)
            }
        }
        assertEquals(Sessions.key.name, client.get("/").bodyAsText())
    }

    @Serializable
    data class Token(val secret: Int)

    @Test
    fun secureCookie() = testApplication {
        install(Sessions) {
            cookie<Token>("SESSION") {
                cookie.path = "/token"
                cookie.httpOnly = true
                cookie.extensions["SameSite"] = "strict"
                cookie.maxAgeInSeconds = 1.days.inWholeSeconds
                cookie.secure = true
            }
        }
        routing {
            get("/token") {
                call.sessions.set(Token(42))
                call.respond(HttpStatusCode.OK)
            }
        }

        client.get("https://localhost/token").body<Unit>()
    }

    @Test
    fun testMissingSession() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Monitoring) {
                assertFailsWith<SessionNotYetConfiguredException> {
                    call.sessions.get<EmptySession>()
                }
                call.respondText("OK")
                finish()
            }
        }
        routing {
            get("/") {
            }
        }
        install(Sessions) {
            cookie<TestUserSession>("name1")
        }

        assertEquals("OK", client.get("/").bodyAsText())
    }

    @Test
    fun testSetCookieNotAdded() = testApplication {
        application {
            install(Sessions) {
                cookie<TestUserSession>(cookieName)
            }
            routing {
                post("/0") {
                    call.sessions.set(TestUserSession("id", emptyList()))
                }
                get("/user") {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        val setCookieHeader = client.post("/0").headers[HttpHeaders.SetCookie]
        assertNotNull(setCookieHeader)

        val cookie = parseServerSetCookieHeader(setCookieHeader)
        client.get("/1") {
            header(HttpHeaders.Cookie, "$cookieName=${cookie.value}")
            cookie(cookie.name, cookie.value)
        }.apply {
            assertNull(headers[HttpHeaders.SetCookie])
        }
    }
}

@Serializable
class EmptySession

@Serializable
data class TestUserSession(val userId: String, val cart: List<String>)

@Serializable
data class TestUserSessionB(val userId: String, val cart: List<String>)
