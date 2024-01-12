/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.freemarker

import freemarker.cache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import java.util.zip.*
import kotlin.test.*
import kotlin.text.Charsets

class FreeMarkerTest {
    @Test
    fun testName() = testApplication {
        application {
            setUpTestTemplates()
            install(ConditionalHeaders)
            routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model, "e"))
                }
            }
        }
        val response = client.get("/")

        val content = response.bodyAsText()
        assertNotNull(content)
        val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
        assertEquals(expectedContent, content.lines())
        val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testEmptyModel() = testApplication {
        application {
            setUpTestTemplates()
            routing {
                get("/") {
                    call.respondTemplate("empty.ftl")
                }
            }
        }
        val response = client.get("/")
        val content = response.bodyAsText()
        assertNotNull(content)
        assertEquals(listOf("<p>Hello, Anonymous</p>", "<h1>Hi!</h1>"), content.lines())
        val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        assertNull(response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testCompression() = testApplication {
        application {
            setUpTestTemplates()
            install(Compression) {
                gzip { minimumSize(10) }
            }
            install(ConditionalHeaders)

            routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respondTemplate("test.ftl", model, "e")
                }
            }
        }

        val response = client.get("/") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }

        val content = GZIPInputStream(response.bodyAsChannel().toByteArray().inputStream()).reader().readText()
        assertEquals(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"), content.lines())
        val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
    }

    @Test
    fun testWithoutEtag() = testApplication {
        application {
            setUpTestTemplates()
            install(ConditionalHeaders)

            routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model))
                }
            }
        }
        val response = client.get("/")
        val content = response.bodyAsText()
        assertNotNull(content)
        assertEquals(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"), content.lines())
        val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        assertEquals(null, response.headers[HttpHeaders.ETag])
    }

    @Test
    fun canRespondAppropriately() = testApplication {
        application {
            setUpTestTemplates()
            install(ConditionalHeaders)

            routing {
                val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

                get("/") {
                    call.respondTemplate("test.ftl", model)
                }
            }
        }

        val content = client.get("/").bodyAsText()

        assertNotNull(content)
        val lines = content.lines()
        assertEquals("<p>Hello, 1</p>", lines[0])
        assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
    }

    @Test
    fun testErrorInContent() = testApplication {
        application {
            setUpTestTemplates()
            install(StatusPages) {
                exception<Throwable> { call, _ ->
                    call.respond("Error: template exception")
                }
            }
            install(ConditionalHeaders)
            routing {
                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", null))
                }
            }
        }
        val content = client.get("/").bodyAsText()
        assertEquals("Error: template exception", content)
    }

    @Test
    fun testContentNegotiationInvokedAfter() = testApplication {
        application {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, alwaysFailingConverter)
            }
            setUpTestTemplates()
            install(ConditionalHeaders)

            routing {
                val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

                get("/") {
                    call.respondTemplate("test.ftl", model)
                }
            }
        }

        val lines = client.get("/").bodyAsText().lines()
        assertEquals("<p>Hello, 1</p>", lines[0])
        assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
    }

    private fun Application.setUpTestTemplates() {
        val bax = "$"

        install(FreeMarker) {
            templateLoader = StringTemplateLoader().apply {
                putTemplate(
                    "test.ftl",
                    """
                        <p>Hello, $bax{id}</p>
                        <h1>$bax{title}</h1>
                    """.trimIndent()
                )
                putTemplate(
                    "empty.ftl",
                    """
                        <p>Hello, Anonymous</p>
                        <h1>Hi!</h1>
                    """.trimIndent()
                )
            }
        }
    }

    companion object {
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
