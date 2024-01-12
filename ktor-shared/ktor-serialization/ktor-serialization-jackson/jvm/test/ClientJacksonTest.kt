/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.fasterxml.jackson.annotation.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.jackson.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.test.*

class ClientJacksonTest : AbstractClientContentNegotiationTest() {
    private val converter = JacksonConverter()

    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")
    override val webSocketsConverter: WebsocketContentConverter = JacksonWebsocketContentConverter()

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, converter)
    }

    override fun createRoutes(routing: Route): Unit = with(routing) {
        super.createRoutes(routing)

        post("/jackson") {
            assertEquals("""{"value":"request"}""", call.receive())
            call.respondText(
                """{"ok":true,"result":[{"value":"response","ignoredValue":"not_ignored"}]}""",
                ContentType.Application.Json
            )
        }
        post("/headers") {
            call.respondText(
                "${call.request.headers[HttpHeaders.TransferEncoding]}" +
                    ":" +
                    "${call.request.headers[HttpHeaders.ContentLength]}"
            )
        }
    }

    @Test
    fun testJackson() = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "jackson")
                setBody(Jackson("request", "ignored"))
                contentType(ContentType.Application.Json)
            }.body<Response<List<Jackson>>>()

            assertTrue(response.ok)
            val list = response.result!!
            assertEquals(1, list.size)
            assertEquals(Jackson("response", null), list[0]) // encoded with GsonConverter
        }
    }

    @Test
    fun testChunkedEncodingByDefault() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                jackson()
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Jackson("request", "ignored"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("chunked:null", response)
        }
    }

    @Test
    fun testNotChunkedEncodingIfSet() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                jackson(streamRequestBody = false)
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Jackson("request", "ignored"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("null:19", response)
        }
    }

    @Test
    @Ignore
    override fun testSealed() {
    }

    data class Jackson(val value: String, @JsonIgnore val ignoredValue: String?)
}
