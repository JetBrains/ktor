/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.dispatcher.*
import java.nio.file.*
import kotlin.io.path.*
import kotlin.test.*

class CustomSocketCIOJvmTest {

    @Test
    fun connectServerAndClientOverUnixSocket() = testSuspend {
        val socket = Files.createTempFile("network", "socket").absolutePathString()

        val client = HttpClient(CIOSocketClient(socket))
        val server = embeddedServer(CIOSocket(socket)) {
            routing {
                get("/hello") {
                    call.respondText("Get from Socket")
                }
                post("/hello") {
                    call.respondText("Post from Socket")
                }
            }
        }.start(wait = false)
        assertEquals("Get from Socket", client.get("/hello").bodyAsText())
        assertEquals("Post from Socket", client.post("/hello").bodyAsText())
        server.stop(1000L, 1000L)
    }
}
