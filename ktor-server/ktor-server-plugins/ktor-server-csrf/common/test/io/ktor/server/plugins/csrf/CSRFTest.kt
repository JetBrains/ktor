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
import io.ktor.util.logging.*
import kotlin.test.*

class CSRFTest {

    @Test
    fun allowOrigin() = testApplication {
        configureCSRF {
            allowOrigin("https://localhost:8080")
        }

        assertEquals(200, client.post("/no-csrf").status.value)

        client.post("/csrf").let { response ->
            assertEquals(400, response.status.value)
            assertEquals("Cross-site request validation failed; missing \"Origin\" header", response.bodyAsText())
        }

        client.post("/csrf") {
            headers[HttpHeaders.Origin] = "https://127.0.0.1:8080"
        }.let { response ->
            assertEquals(400, response.status.value)
            assertEquals(
                "Cross-site request validation failed; unexpected \"Origin\" header value [https://127.0.0.1:8080]",
                response.bodyAsText()
            )
        }

        client.post("/csrf") {
            headers[HttpHeaders.Origin] = "https://localhost:8080"
        }.let { response ->
            assertEquals(200, response.status.value)
        }

        client.post("/csrf") {
            headers[HttpHeaders.Referrer] = "http://localhost:8080/redirect/from-here"
        }.let { response ->
            assertEquals(200, response.status.value)
        }
    }

    @Test
    fun originMatchesHost() = testApplication {
        configureCSRF {
            originMatchesHost()
        }

        assertEquals(200, client.post("/no-csrf").status.value)

        client.post("/csrf") {
            headers[HttpHeaders.Origin] = "http://localhost:8080"
            headers[HttpHeaders.Host] = "127.0.0.1:8080"
        }.let { response ->
            assertEquals(400, response.status.value)
            assertEquals(
                "Cross-site request validation failed; " +
                    "expected \"Origin\" [localhost:8080] host to match \"Host\" [127.0.0.1:8080] header value",
                response.bodyAsText()
            )
        }

        client.post("/csrf") {
            headers[HttpHeaders.Origin] = "http://localhost:8080"
            headers[HttpHeaders.Host] = "localhost"
        }.let { response ->
            assertEquals(400, response.status.value)
            assertEquals(
                "Cross-site request validation failed; " +
                    "expected \"Origin\" [localhost:8080] host to match \"Host\" [localhost] header value",
                response.bodyAsText()
            )
        }

        client.post("/csrf") {
            headers[HttpHeaders.Origin] = "http://localhost:8080"
            headers[HttpHeaders.Host] = "localhost:8080"
        }.let { response ->
            assertEquals(200, response.status.value)
        }
    }

    @Test
    fun customHeader() = testApplication {
        configureCSRF {
            checkHeader("X-CSRF") { it == "1" }
        }

        client.post("/csrf").let { response ->
            assertEquals(400, response.status.value)
            assertEquals("Cross-site request validation failed; missing \"X-CSRF\" header", response.bodyAsText())
        }

        client.post("/csrf") {
            headers["X-CSRF"] = "0"
        }.let { response ->
            assertEquals(400, response.status.value)
            assertEquals(
                "Cross-site request validation failed; unexpected \"X-CSRF\" header value [0]",
                response.bodyAsText()
            )
        }

        client.post("/csrf") {
            headers["X-CSRF"] = "1"
        }.let { response ->
            assertEquals(200, response.status.value)
        }
    }

    @Test
    fun onFailureOverride() = testApplication {
        val customErrorMessage = "Hands off mah cookies!"

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

        client.post("/csrf") {
            headers[HttpHeaders.Origin] = "http://localhost:8080"
            headers["X-CSRF"] = "http://localhost:8080".hashCode().toString(32)
        }.let { response ->
            assertEquals(200, response.status.value)
        }

        client.post("/csrf").let { response ->
            assertEquals(403, response.status.value)
            assertEquals(customErrorMessage, response.bodyAsText())
        }
    }

    @Test
    fun ignoresSafeMethods() = testApplication {
        configureCSRF {
            originMatchesHost()
        }
        val safeMethods = listOf(HttpMethod.Get, HttpMethod.Options, HttpMethod.Head)
        val invalidRequestWithMethod: (HttpMethod) -> HttpRequestBuilder.() -> Unit = { m ->
            {
                method = m
                headers[HttpHeaders.Origin] = "http://localhost:8080"
                headers[HttpHeaders.Host] = "127.0.0.1:8080"
            }
        }

        for (m in HttpMethod.DefaultMethods - safeMethods.toSet()) {
            assertEquals(400, client.request("/csrf", invalidRequestWithMethod(m)).status.value)
        }

        for (m in safeMethods) {
            assertEquals(200, client.request("/csrf", invalidRequestWithMethod(m)).status.value)
        }
    }

    @Test
    fun onFailureDefaultResponse() = testApplication {
        var errorMessageVariable = ""

        configureCSRF {
            checkHeader("X-CSRF") { csrfHeader ->
                request.headers[HttpHeaders.Origin]?.let { origin ->
                    csrfHeader == origin.hashCode().toString(32)
                } == true
            }
            onFailure {
                errorMessageVariable = it
            }
        }

        client.post("/csrf") {
            headers[HttpHeaders.Origin] = "http://localhost:8080"
            headers["X-CSRF"] = "http://localhost:8080".hashCode().toString(32)
        }.let { response ->
            assertEquals(200, response.status.value)
        }

        client.post("/csrf").let { response ->
            val expectedMessage = "missing \"X-CSRF\" header"
            assertEquals(400, response.status.value)
            assertEquals("Cross-site request validation failed; $expectedMessage", response.bodyAsText())
            assertEquals(errorMessageVariable, expectedMessage)
        }
    }

    @Test
    fun worksWithDefaultPort() = testApplication {
        configureCSRF {
            originMatchesHost()
        }

        client.post("/csrf") {
            header("Host", "localhost:80")
            header("Origin", "http://localhost:80")
        }.let { response ->
            assertEquals(200, response.status.value)
            assertEquals("success", response.bodyAsText())
        }
    }

    @Test
    fun logsWarningWhenMisconfigured() = testApplication {
        val warnings = mutableListOf<String>()
        val testLogger = object : Logger by KtorSimpleLogger("") {
            override fun info(message: String) {
                message.let(warnings::add)
            }
        }

        environment {
            log = testLogger
        }
        configureCSRF {}

        for (allowedOrigin in listOf("http://localhost", "http://127.0.0.1", "http://0.0.0.0")) {
            client.post("/csrf") {
                header("Origin", allowedOrigin)
            }.let { response ->
                assertEquals(200, response.status.value)
                assertEquals("success", response.bodyAsText())
            }
        }

        client.post("/csrf") {
            header("Origin", "http://website.com")
        }.let { response ->
            assertEquals(400, response.status.value)
            assertEquals(
                "Cross-site request validation failed; " +
                    "unexpected \"Origin\" header value [http://website.com]",
                response.bodyAsText()
            )
        }

        assertEquals(
            "CSRF configuration is empty; defaulting to allow only local origins",
            warnings.firstOrNull { it.contains("CSRF") }
        )
    }

    private fun ApplicationTestBuilder.configureCSRF(csrfOptions: CSRFConfig.() -> Unit) {
        routing {
            route("/csrf") {
                install(CSRF) {
                    csrfOptions()
                }
                handle {
                    call.respondText("success")
                }
                post {
                    call.respondText("success")
                }
            }
            route("/no-csrf") {
                handle {
                    call.respondText("success")
                }
            }
        }
    }
}
