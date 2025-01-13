/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*
import java.net.*
import kotlin.random.*
import kotlin.test.*

class JettyHttpConfigurationTest {

    private fun findFreePort() = ServerSocket(0).use { it.localPort }

    companion object {

        @JvmStatic
        fun testCases() = listOf(
            Arguments.of(8, 6, HttpStatusCode.RequestHeaderFieldTooLarge),
            Arguments.of(16, 6, HttpStatusCode.NoContent),
            Arguments.of(16, 8, HttpStatusCode.RequestHeaderFieldTooLarge)
        )
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun `test HttpConfiguration with request header size example`(
        requestHeaderSizeConfigKB: Int,
        headerSizeKB: Int,
        expectedStatusCode: HttpStatusCode
    ) = runTest {
        val serverPort = findFreePort()

        embeddedServer(Jetty, configure = {
            httpConfiguration = {
                requestHeaderSize = requestHeaderSizeConfigKB * 1024
            }
            connector { port = serverPort }
        }) {
            routing {
                get("/") {
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }.start(wait = false)

        val createHeaderValue = { List(headerSizeKB * 1024) { Random.nextInt(33, 127).toChar() }.joinToString("") }

        val response = HttpClient().use { client ->
            client.get {
                url("http://127.0.0.1:$serverPort/")
                header("X-Custom-Large-Header-1", createHeaderValue())
                header("X-Custom-Large-Header-2", createHeaderValue())
            }
        }

        assertEquals(expectedStatusCode, response.status)
    }
}
