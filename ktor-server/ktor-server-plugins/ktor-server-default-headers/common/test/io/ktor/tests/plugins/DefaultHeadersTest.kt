/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class DefaultHeadersTest {

    @Test
    fun addsServerHeaderWithFallbackPackageNameAndVersion() = testApplication {
        install(DefaultHeaders)
        routing {
            get { call.respond("OK") }
        }

        client.get("/").let { response ->
            val actual = response.headers["Server"]
            assertNotNull(actual)
            assertTrue(actual.startsWith("Ktor/"))
        }
    }

    @Test
    fun serverHeaderIsNotModifiedIfPresent() = testApplication {
        install(DefaultHeaders) {
            header(HttpHeaders.Server, "xserver/1.0")
        }

        routing {
            get { call.respond("OK") }
        }

        assertEquals("xserver/1.0", client.get("/").headers["Server"])
    }

    @Test
    fun testSubrouteInstall() = testApplication {
        routing {
            route("1") {
                install(DefaultHeaders) {}
                get { call.respond("response") }
            }
            get("2") { call.respond("response") }
        }

        client.get("/1").let { response ->
            val actual = response.headers["Server"]
            assertNotNull(actual)
            assertTrue(actual.startsWith("Ktor/"))
        }

        assertNull(client.get("/2").headers["Server"])
    }

    @Test
    fun testDate() = testApplication {
        var now = 1569882841014
        install(DefaultHeaders) {
            clock = DefaultHeadersConfig.Clock { now }
        }

        application {
            intercept(ApplicationCallPipeline.Call) {
                call.respondText("OK")
            }
        }

        assertEquals("Mon, 30 Sep 2019 22:34:01 GMT", client.get("/").headers[HttpHeaders.Date])

        now += 999

        assertEquals("Mon, 30 Sep 2019 22:34:01 GMT", client.get("/").headers[HttpHeaders.Date])

        now++

        assertEquals("Mon, 30 Sep 2019 22:34:02 GMT", client.get("/").headers[HttpHeaders.Date])
    }

    @Test
    fun testCustomHeader() = testApplication {
        install(DefaultHeaders) {
            header("X-Test", "123")
        }

        assertEquals("123", client.get("/").headers["X-Test"])
    }

    @Test
    fun testDefaultServerHeader() = testApplication {
        install(DefaultHeaders)

        client.get("/").let { response ->
            val serverHeader = response.headers[HttpHeaders.Server]
            assertNotNull(serverHeader)
            assertTrue("Server header invalid: $serverHeader") { "Ktor" in serverHeader }
        }
    }

    @Test
    fun testCustomServerHeader() = testApplication {
        install(DefaultHeaders) {
            header(HttpHeaders.Server, "MyServer")
        }

        assertEquals("MyServer", client.get("/").headers[HttpHeaders.Server])
    }

    @Test
    fun testCustomServerHeaderDoesntDuplicate() = testApplication {
        install(
            createApplicationPlugin("test") {
                on(CallSetup) {
                    it.response.header(HttpHeaders.Server, "MyServer")
                }
            }
        )
        install(DefaultHeaders) {
            header(HttpHeaders.Server, "MyServer1")
        }

        assertEquals(listOf("MyServer"), client.get("/").headers.getAll(HttpHeaders.Server))
    }
}
