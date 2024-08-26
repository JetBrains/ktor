/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.serialization.test

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import java.nio.charset.*
import kotlin.test.*

public abstract class AbstractServerSerializationTest {
    private val uc = "\u0422"

    protected abstract val defaultContentType: ContentType
    protected abstract val customContentType: ContentType
    protected abstract fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType)

    protected abstract fun simpleSerialize(any: MyEntity): ByteArray
    protected abstract fun simpleDeserialize(t: ByteArray): MyEntity
    protected abstract fun simpleDeserializeList(t: ByteArray, charset: Charset = Charsets.UTF_8): List<MyEntity>

    private fun withTestSerializingApplication(
        block: suspend TestApplicationEngine.() -> Unit
    ): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            configureContentNegotiation(defaultContentType)
        }

        application.routing {
            get("/list") {
                call.respond(testEntities)
            }
            get("/flow") {
                call.respond(testEntities.asFlow())
            }
            get("/map") {
                val model = mapOf("id" to 1, "title" to "Hello, World!", "unicode" to uc)
                call.respond(model)
            }
            post("/map") {
                val map = call.receive<Map<*, *>>()
                val text = map.entries.joinToString { "${it.key}=${it.value}" }
                call.respond(text)
            }
            get("/entity") {
                val model = MyEntity(
                    777,
                    "Cargo",
                    listOf(ChildEntity("Qube", 1), ChildEntity("Sphere", 2), ChildEntity(uc, 3))
                )
                call.respond(model)
            }
            post("/entity") {
                val entity = call.receive<MyEntity>()
                call.respond(entity)
            }
            post("/null") {
                val result = call.receiveNullable<NullValues?>() ?: "OK"
                call.respondText(result.toString())
            }
        }

        runBlocking {
            block()
        }
    }

    @Test
    public fun testEntityNoAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/entity").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.id)
        }
    }

    @Test
    public fun testEntityWithAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/entity") {
            addHeader(HttpHeaders.Accept, defaultContentType.toString())
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.id)
        }
    }

    @Test
    public fun testDumpWithMultipleAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/entity") {
            addHeader(HttpHeaders.Accept, "$defaultContentType;q=1,text/plain;q=0.9")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.id)
        }
    }

    @Test
    public fun testListNoAcceptUtf8(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/list").let { call -> verifyListResponse(call.response, Charsets.UTF_8) }
    }

    @Test
    public fun testListAcceptUtf16(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/list") {
            addHeader("Accept-Charset", "UTF-16")
        }.let { call -> verifyListResponse(call.response, Charsets.UTF_16) }
    }

    @Test
    public open fun testFlowNoAcceptUtf8(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/flow").let { call ->
            verifyListResponse(call.response, Charsets.UTF_8)
            assertEquals("chunked", call.response.headers[HttpHeaders.TransferEncoding])
        }
    }

    @Test
    public open fun testFlowAcceptUtf16(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/flow") {
            addHeader("Accept-Charset", "UTF-16")
        }.let { call -> verifyListResponse(call.response, Charsets.UTF_16) }
    }

    private fun verifyListResponse(response: TestApplicationResponse, charset: Charset) {
        assertEquals(HttpStatusCode.OK, response.status())
        val bytes = response.byteContent
        assertNotNull(bytes)
        val listEntity = simpleDeserializeList(bytes, charset)
        assertEquals(testEntities, listEntity)
    }

    @Test
    public open fun testMap(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/map") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""{"id":1,"title":"Hello, World!","unicode":"$uc"}"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json, ContentType.parse(contentTypeText))
        }

        handleRequest(HttpMethod.Post, "/map") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""{"id":1.0,"title":"Hello, World!","unicode":"$uc"}""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""id=1.0, title=Hello, World!, unicode=$uc"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    public fun testPostEntity(): Unit = withTestSerializingApplication {
        val body = testEntities.first()
        handleRequest(HttpMethod.Post, "/entity") {
            addHeader("Content-Type", defaultContentType.toString())
            setBody(simpleSerialize(body))
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals(body, simpleDeserialize(response.byteContent!!))
        }
    }

    @Test
    public open fun testReceiveNullValue(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Post, "/null") {
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("null")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
    }

    @Serializable
    private class NullValues

    @Serializable
    public data class MyEntity(val id: Int, val name: String, val children: List<ChildEntity>)

    @Serializable
    public data class ChildEntity(val item: String, val quantity: Int)

    private companion object {
        private val testEntities = listOf(
            MyEntity(111, "ൠ", listOf(ChildEntity("item1", 1), ChildEntity("item2", 2))),
            MyEntity(222, "ൠ1", listOf(ChildEntity("item3", 3), ChildEntity("item4", 5)))
        )
    }
}
