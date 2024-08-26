/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.velocity

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
import io.ktor.server.velocity.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import org.apache.velocity.runtime.resource.loader.*
import org.apache.velocity.runtime.resource.util.*
import java.util.zip.*
import kotlin.test.*
import kotlin.text.Charsets

class VelocityTest {
    @Test
    fun testName() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)
            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(VelocityContent("test.vl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
                assertEquals(expectedContent, response.content!!.lines())

                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testCompression() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(Compression) {
                gzip { minimumSize(10) }
            }
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(VelocityContent("test.vl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
                assertEquals(expectedContent, content.lines())

                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testWithoutEtag() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(VelocityContent("test.vl", model))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                val content = response.content
                assertNotNull(content)
                val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
                assertEquals(expectedContent, content.lines())

                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals(null, response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun canRespondAppropriately() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

                get("/") {
                    call.respondTemplate("test.vl", model)
                }
            }

            val call = handleRequest(HttpMethod.Get, "/")

            with(call.response) {
                assertNotNull(content)

                val lines = content!!.lines()

                assertEquals("<p>Hello, 1</p>", lines[0])
                assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
            }
        }
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
                    call.respondTemplate("test.vl", model)
                }
            }
        }

        val lines = client.get("/").bodyAsText().lines()
        assertEquals("<p>Hello, 1</p>", lines[0])
        assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
    }

    private fun Application.setUpTestTemplates() {
        val bax = "$"

        install(Velocity) {
            setProperty("resource.loader", "string")
            addProperty("resource.loader.string.class", StringResourceLoader::class.java.name)
            addProperty("resource.loader.string.repository.name", "myRepo")

            StringResourceRepositoryImpl().apply {
                putStringResource(
                    "test.vl",
                    """
                    <p>Hello, ${bax}id</p>
                    <h1>${bax}title</h1>
                    """.trimIndent()
                )
                StringResourceLoader.setRepository("myRepo", this)
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
