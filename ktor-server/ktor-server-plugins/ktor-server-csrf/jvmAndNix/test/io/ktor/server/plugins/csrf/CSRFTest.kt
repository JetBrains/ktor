/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.csrf

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class CSRFTest {

    @Test
    fun allowOrigin() {
        testApplication {
            configureCSRF {
                allowOrigin("https://localhost:8080")
            }

            assertEquals(200, client.get("/no-csrf").status.value)

            client.get("/csrf").let { response ->
                assertEquals(400, response.status.value)
                assertEquals("Cross-site request validation failed; missing \"Origin\" header", response.bodyAsText())
            }

            client.get("/csrf") {
                headers[HttpHeaders.Origin] = "https://127.0.0.1:8080"
            }.let { response ->
                assertEquals(400, response.status.value)
                assertEquals(
                    "Cross-site request validation failed; unexpected \"Origin\" header value [https://127.0.0.1:8080]",
                    response.bodyAsText()
                )
            }

            client.get("/csrf") {
                headers[HttpHeaders.Origin] = "https://localhost:8080"
            }.let { response ->
                assertEquals(200, response.status.value)
            }

            client.get("/csrf") {
                headers[HttpHeaders.Referrer] = "http://localhost:8080/redirect/from-here"
            }.let { response ->
                assertEquals(200, response.status.value)
            }
        }
    }

    @Test
    fun originMatchesHost() {
        testApplication {
            configureCSRF {
                originMatchesHost()
            }

            assertEquals(200, client.get("/no-csrf").status.value)

            client.get("/csrf") {
                headers[HttpHeaders.Origin] = "http://localhost:8080"
                headers[HttpHeaders.Host] = "127.0.0.1:8080"
            }.let { response ->
                assertEquals(400, response.status.value)
                assertEquals(
                    "Cross-site request validation failed; " +
                        "expected \"Origin\" [localhost] host to match \"Host\" [127.0.0.1:8080] header value",
                    response.bodyAsText()
                )
            }

            client.get("/csrf") {
                headers[HttpHeaders.Origin] = "http://localhost:8080"
                headers[HttpHeaders.Host] = "localhost:8080"
            }.let { response ->
                assertEquals(200, response.status.value)
            }
        }
    }

    @Test
    fun customHeader() {
        testApplication {
            configureCSRF {
                checkHeader("X-CSRF") { it == "1" }
            }

            client.get("/csrf").let { response ->
                assertEquals(400, response.status.value)
                assertEquals("Cross-site request validation failed; missing \"X-CSRF\" header", response.bodyAsText())
            }

            client.get("/csrf") {
                headers["X-CSRF"] = "0"
            }.let { response ->
                assertEquals(400, response.status.value)
                assertEquals(
                    "Cross-site request validation failed; unexpected \"X-CSRF\" header value [0]",
                    response.bodyAsText()
                )
            }

            client.get("/csrf") {
                headers["X-CSRF"] = "1"
            }.let { response ->
                assertEquals(200, response.status.value)
            }
        }
    }

    @Test
    fun onFailureOverride() {
        val customErrorMessage = "Hands off mah cookies!"

        testApplication {
            configureCSRF {
                checkHeader("X-CSRF") { csrfHeader ->
                    request.headers[HttpHeaders.Origin]?.let { origin ->
                        csrfHeader == origin.hashCode().toString(32)
                    } == true
                }
                onFailure {
                    respondText(customErrorMessage, status = HttpStatusCode.Forbidden)
                }
            }

            client.get("/csrf") {
                headers[HttpHeaders.Origin] = "http://localhost:8080"
                headers["X-CSRF"] = "http://localhost:8080".hashCode().toString(32)
            }.let { response ->
                assertEquals(200, response.status.value)
            }

            client.get("/csrf").let { response ->
                assertEquals(403, response.status.value)
                assertEquals(customErrorMessage, response.bodyAsText())
            }
        }
    }

    private fun ApplicationTestBuilder.configureCSRF(csrfOptions: CSRFConfig.() -> Unit) {
        routing {
            route("/csrf") {
                install(CSRF) {
                    csrfOptions()
                }
                get {
                    call.respondText("success")
                }
            }
            route("/no-csrf") {
                get {
                    call.respondText("success")
                }
            }
        }
    }
}
