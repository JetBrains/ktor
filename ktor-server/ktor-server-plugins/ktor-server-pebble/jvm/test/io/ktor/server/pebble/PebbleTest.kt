/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.pebble

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
import io.pebbletemplates.pebble.loader.*
import java.util.zip.*
import kotlin.test.*
import kotlin.text.Charsets

@Suppress("DEPRECATION")
class PebbleTest {

    @Test
    fun `Fill template and expect correct rendered content`() {
        withTestApplication {
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel, etag = "e"))
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
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel, etag = "e"))
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
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel, etag = "e"))
                }
            }

            assertEquals("\"e\"", handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun `Render empty model`() {
        withTestApplication {
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithoutPlaceholder, emptyMap(), etag = "e"))
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
            application.setupPebble()
            application.install(Compression) {
                gzip { minimumSize(10) }
            }
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respondTemplate(TemplateWithPlaceholder, DefaultModel, etag = "e")
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
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel))
                }
            }

            assertEquals(null, handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun `Render template in Spanish with es accept language`() {
        testApplication {
            application {
                setupPebble()
                install(ConditionalHeaders)
                routing {
                    get("/") {
                        call.respond(PebbleContent(TemplateI18N, emptyMap()))
                    }
                }
            }

            client.get("/") {
                headers.append(HttpHeaders.AcceptLanguage, "es")
            }.apply {
                assertContains(bodyAsText(), "<p>Hola, mundo!</p>")
            }
        }
    }

    @Test
    fun `Render template in English with en accept language`() {
        testApplication {
            application {
                setupPebble()
                install(ConditionalHeaders)
                routing {
                    get("/") {
                        call.respond(PebbleContent(TemplateI18N, emptyMap()))
                    }
                }
            }

            client.get("/") {
                headers.append(HttpHeaders.AcceptLanguage, "en")
            }.apply {
                assertContains(bodyAsText(), "<p>Hello, world!</p>")
            }
        }
    }

    @Test
    fun `Render template in default language with no valid accept language header set`() {
        testApplication {
            application {
                setupPebble()
                install(ConditionalHeaders)
                routing {
                    get("/") {
                        call.respond(PebbleContent(TemplateI18N, emptyMap()))
                    }
                }
            }

            client.get("/") {
                headers.append(HttpHeaders.AcceptLanguage, "jp")
            }.apply {
                assertContains(bodyAsText(), "<p>Hello, world!</p>")
            }
        }
    }

    @Test
    fun `Render template in default language with no accept language header set`() {
        testApplication {
            application {
                setupPebble()
                install(ConditionalHeaders)
                routing {
                    get("/") {
                        call.respond(PebbleContent(TemplateI18N, emptyMap()))
                    }
                }
            }

            client.get("/").apply {
                assertContains(bodyAsText(), "<p>Hello, world!</p>")
            }
        }
    }

    @Test
    fun `Content Negotiation invoked after`() = testApplication {
        application {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, alwaysFailingConverter)
            }
            setupPebble()

            routing {
                get("/") {
                    call.respond(PebbleContent(TemplateI18N, emptyMap()))
                }
            }
        }

        val response = client.get("/") { headers.append(HttpHeaders.AcceptLanguage, "es") }
        assertEquals("<p>Hola, mundo!</p>", response.bodyAsText())
    }

    private fun Application.setupPebble() {
        install(Pebble) {
            loader(StringLoader())
            availableLanguages = mutableListOf("en", "es")
        }
    }

    companion object {
        private val DefaultModel = mapOf("id" to 1, "title" to "Hello World!")

        private val TemplateWithPlaceholder
            get() = """
                <p>Hello, {{id}}</p>
                <h1>{{title}}</h1>
            """.trimIndent()
        private val TemplateWithoutPlaceholder: String
            get() = """
                <p>Hello, Anonymous</p>
                <h1>Hi!</h1>
            """.trimIndent()

        private val TemplateI18N: String
            get() = """
                <p>{{ i18n("i18n_test", "hello_world") }}</p>
            """.trimIndent()

        private val alwaysFailingConverter = object : ContentConverter {
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
    }
}
