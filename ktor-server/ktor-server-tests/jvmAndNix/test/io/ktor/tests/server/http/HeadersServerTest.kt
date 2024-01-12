/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class HeadersServerTest {

    @Test
    fun headersReturnNullWhenEmpty(): Unit = withTestApplication {
        application.routing {
            get("/") {
                assertNull(call.request.headers["X-Nonexistent-Header"])
                assertNull(call.request.headers.getAll("X-Nonexistent-Header"))

                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("OK", call.response.content)
        }
    }

    @Test
    fun returnConnectionHeaderSetByServer(): Unit = withTestApplication {
        application.routing {
            get("/") {
                call.response.header(name = HttpHeaders.Connection, value = "close")
                call.respondText("OK")
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(name = HttpHeaders.Connection, value = "keep-alive")
        }.let {
            assertEquals(listOf("close"), it.response.headers.values(HttpHeaders.Connection))
        }
    }
}
