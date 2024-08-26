/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.serialization.test.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.nio.charset.*
import kotlin.test.*

class ServerJacksonTest : AbstractServerSerializationTest() {
    private val objectMapper = jacksonObjectMapper()
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, JacksonConverter(objectMapper, false))
    }

    override fun simpleDeserialize(t: ByteArray): MyEntity {
        return objectMapper.readValue(String(t), jacksonTypeRef<MyEntity>())
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset): List<MyEntity> {
        return objectMapper.readValue(String(t, charset), jacksonTypeRef<List<MyEntity>>())
    }

    override fun simpleSerialize(any: MyEntity): ByteArray {
        return objectMapper.writeValueAsBytes(any)
    }

    @Test
    fun testWithUTF16() = withTestApplication {
        val uc = "\u0422"
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter())
        }
        application.routing {
            val model = mapOf("id" to 1, "title" to "Hello, World!", "unicode" to uc)
            get("/") {
                call.respond(model)
            }
            post("/") {
                val map = call.receive<Map<*, *>>()
                val text = map.entries.joinToString { "${it.key}=${it.value}" }
                call.respond(text)
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
            addHeader("Accept-Charset", "UTF-16")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""{"id":1,"title":"Hello, World!","unicode":"$uc"}"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_16), ContentType.parse(contentTypeText))
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json; charset=UTF-16")
            setBody("""{"id":1,"title":"Hello, World!","unicode":"$uc"}""".toByteArray(charset = Charsets.UTF_16))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""id=1, title=Hello, World!, unicode=$uc"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testPrettyPrinter() = withTestApplication {
        application.install(ContentNegotiation) {
            jackson {
                configure(SerializationFeature.INDENT_OUTPUT, true)
            }
        }

        application.routing {
            get("/") {
                call.respond(mapOf("a" to 1, "b" to 2))
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "application/json")
        }.response.let { response ->
            assertEquals("{\n  \"a\" : 1,\n  \"b\" : 2\n}", response.content)
        }
    }

    @Test
    fun testCustomKotlinModule() = withTestApplication {
        application.install(ContentNegotiation) {
            jackson {
                registerModule(
                    KotlinModule.Builder()
                        .withReflectionCacheSize(512)
                        .configure(KotlinFeature.NullToEmptyCollection, enabled = false)
                        .configure(KotlinFeature.NullToEmptyMap, enabled = false)
                        .configure(KotlinFeature.NullIsSameAsDefault, enabled = true)
                        .configure(KotlinFeature.SingletonSupport, enabled = false)
                        .configure(KotlinFeature.StrictNullChecks, enabled = false)
                        .build()
                )
            }
        }

        application.routing {
            post("/") {
                call.respond(call.receive<WithDefaultValueEntity>())
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.Accept, "application/json")
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("""{"value":null}""")
        }.response.let { response ->
            assertEquals("""{"value":"asd"}""", response.content)
        }
    }
}

data class WithDefaultValueEntity(val value: String = "asd")
