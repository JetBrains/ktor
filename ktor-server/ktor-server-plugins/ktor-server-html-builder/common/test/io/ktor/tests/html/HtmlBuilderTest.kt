/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.html

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlinx.html.*
import kotlin.test.*

class HtmlBuilderTest {
    @Test
    fun testName() = testApplication {
        routing {
            get("/") {
                val name = call.parameters["name"]
                call.respondHtml {
                    body {
                        h1 {
                            +"Hello, $name"
                        }
                    }
                }
            }
        }

        client.get("/?name=John").let { response ->
            val lines = response.bodyAsText()
            assertEquals(
                """<!DOCTYPE html>
<html>
  <body>
    <h1>Hello, John</h1>
  </body>
</html>
""",
                lines
            )
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testError() = testApplication {
        install(StatusPages) {
            exception<NotImplementedError> { call, _ ->
                call.respondHtml(HttpStatusCode.NotImplemented) {
                    body {
                        h1 {
                            +"This feature is not implemented yet"
                        }
                    }
                }
            }
        }

        routing {
            get("/") {
                TODO()
            }
        }

        client.get("/?name=John").let { response ->
            assertEquals(HttpStatusCode.NotImplemented, response.status)
            val lines = response.bodyAsText()
            assertEquals(
                """<!DOCTYPE html>
<html>
  <body>
    <h1>This feature is not implemented yet</h1>
  </body>
</html>
""",
                lines
            )
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testErrorInTemplate() = testApplication {
        install(StatusPages) {
            exception<RuntimeException> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            }
        }

        routing {
            get("/") {
                call.respondHtml {
                    head {
                        title("Minimum Working Example")
                    }
                    body {
                        throw RuntimeException("Error!")
                    }
                }
            }
        }

        client.get("/").let { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("Error!", response.bodyAsText())
        }
    }
}
