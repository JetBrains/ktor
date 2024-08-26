/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class HeadersServerTest {

    @Test
    fun headersReturnNullWhenEmpty() = testApplication {
        routing {
            get("/") {
                assertNull(call.request.headers["X-Nonexistent-Header"])
                assertNull(call.request.headers.getAll("X-Nonexistent-Header"))

                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        client.get("/").let { call ->
            assertEquals(HttpStatusCode.OK, call.status)
            assertEquals("OK", call.bodyAsText())
        }
    }

    @Test
    fun returnConnectionHeaderSetByServer() = testApplication {
        routing {
            get("/") {
                call.response.header(name = HttpHeaders.Connection, value = "close")
                call.respondText("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Connection, "keep-alive")
        }.let {
            assertEquals(listOf("close"), it.headers.getAll(HttpHeaders.Connection))
        }
    }
}
