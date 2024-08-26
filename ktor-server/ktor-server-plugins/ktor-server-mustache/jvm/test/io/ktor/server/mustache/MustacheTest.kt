/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.mustache

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import java.util.zip.*
import kotlin.test.*
import kotlin.text.Charsets

class MustacheTest {

    @Test
    fun `Fill template and expect correct rendered content`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->

                val lines = response.content!!.lines()

                assertEquals("<p>Hello, 1</p>", lines[0])
                assertEquals("<h1>Hello World!</h1>", lines[1])
            }
        }
    }

    @Test
    fun `Fill template and expect correct default content type`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->

                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
            }
        }
    }

    @Test
    fun `Fill template and expect eTag set when it is provided`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
                }
            }

            assertEquals("\"e\"", handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun `Render empty model`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithoutPlaceholder, null, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->

                val lines = response.content!!.lines()

                assertEquals("<p>Hello, Anonymous</p>", lines[0])
                assertEquals("<h1>Hi!</h1>", lines[1])
            }
        }
    }

    @Test
    fun `Render template compressed with GZIP`() {
        withTestApplication {
            application.setupMustache()
            application.install(Compression) {
                gzip { minimumSize(10) }
            }
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respondTemplate(TemplateWithPlaceholder, DefaultModel, "e")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()

                val lines = content.lines()

                assertEquals("<p>Hello, 1</p>", lines[0])
                assertEquals("<h1>Hello World!</h1>", lines[1])
            }
        }
    }

    @Test
    fun `Render template without eTag`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel))
                }
            }

            assertEquals(null, handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun `Content Negotiation invoked after`() = testApplication {
        application {
            install(ContentNegotiation) {
                val alwaysFailingConverter = object : ContentConverter {
                    override suspend fun serialize(
                        contentType: ContentType,
                        charset: Charset,
                        typeInfo: TypeInfo,
                        value: Any?
                    ): OutgoingContent? {
                        fail("This converter should be never started for send")
                    }

                    override suspend fun deserialize(
                        charset: Charset,
                        typeInfo: TypeInfo,
                        content: ByteReadChannel
                    ): Any? {
                        fail("This converter should be never started for receive")
                    }
                }
                register(ContentType.Application.Json, alwaysFailingConverter)
            }
            setupMustache()

            routing {
                get("/") {
                    call.respond(MustacheContent("index.hbs", mapOf<String, String>()))
                }
            }
        }

        val response = client.get("/")
        assertEquals("Template", response.bodyAsText().trim())
    }

    private fun Application.setupMustache() {
        install(Mustache)
    }

    companion object {
        private val DefaultModel = mapOf("id" to 1, "title" to "Hello World!")

        private const val TemplateWithPlaceholder = "withPlaceholder.mustache"
        private const val TemplateWithoutPlaceholder = "withoutPlaceholder.mustache"
    }
}
