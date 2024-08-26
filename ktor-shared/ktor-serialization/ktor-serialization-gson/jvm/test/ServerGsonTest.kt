/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.google.gson.*
import com.google.gson.reflect.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.serialization.test.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.nio.charset.*
import kotlin.test.*

class ServerGsonTest : AbstractServerSerializationTest() {
    private val gson = Gson()
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, GsonConverter(gson))
    }

    override fun simpleDeserialize(t: ByteArray): MyEntity {
        return gson.fromJson(String(t), MyEntity::class.java)
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset): List<MyEntity> {
        return gson.fromJson(String(t, charset), object : TypeToken<List<MyEntity>>() {}.type)
    }

    override fun simpleSerialize(any: MyEntity): ByteArray {
        return gson.toJson(any, MyEntity::class.java).toByteArray()
    }

    private data class TextPlainData(val x: Int)

    @Test
    fun testGsonOnTextAny(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            gson()
            register(contentType = ContentType.Text.Any, converter = GsonConverter())
        }

        application.routing {
            post("/") {
                val instance = call.receive<TextPlainData>()
                assertEquals(TextPlainData(777), instance)
                call.respondText("OK")
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "text/plain")
            setBody("{\"x\": 777}")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"x\": 777}")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
    }

    @Test
    fun testReceiveValuesMap() = withTestApplication {
        application.install(ContentNegotiation) {
            gson()
            register(contentType = ContentType.Text.Any, converter = GsonConverter())
        }

        application.routing {
            post("/") {
                val json = call.receive<JsonObject>()

                val expected = JsonObject().apply {
                    add(
                        "hello",
                        JsonObject().apply {
                            addProperty("ktor", "world")
                        }
                    )
                }

                assertEquals(expected, json)
                call.respondText("OK")
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{ hello: { ktor : world } }")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
    }
}
