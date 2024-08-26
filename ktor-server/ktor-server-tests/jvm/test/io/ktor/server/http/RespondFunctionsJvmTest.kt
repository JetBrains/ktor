/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RespondFunctionsJvmTest {
    @Test
    fun testRespondBytes(): Unit = withTestApplication {
        application.routing {
            get("/output-stream") {
                call.respondOutputStream(contentLength = 2) { write(1); write(2) }
            }
            get("/text-writer") {
                call.respondTextWriter(contentLength = 2) { write(1); write(2) }
            }
        }

        handleRequest(HttpMethod.Get, "/output-stream").let { call ->
            assertEquals("1, 2", call.response.byteContent?.joinToString())
            assertEquals("2", call.response.headers[HttpHeaders.ContentLength])
        }
        handleRequest(HttpMethod.Get, "/text-writer").let { call ->
            assertEquals("1, 2", call.response.byteContent?.joinToString())
            assertEquals("2", call.response.headers[HttpHeaders.ContentLength])
        }
    }
}
