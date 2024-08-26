/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.*
import kotlin.test.*

abstract class JsonContentNegotiationTest(val converter: ContentConverter) {
    protected open val extraFieldResult = HttpStatusCode.OK

    @Serializable
    data class Wrapper(val value: String)

    fun startServer(testApplicationEngine: Application) {
        testApplicationEngine.install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        testApplicationEngine.routing {
            post("/") {
                val wrapper = call.receive<Wrapper>()
                call.respond(wrapper.value)
            }
        }
    }

    @Test
    open fun testBadlyFormattedJson(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"value" : "bad_json" """)
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    open fun testJsonWithNullValue(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"val" : "bad_json" } """)
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    open fun testClearValidJson(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"value" : "value" }""")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }
    }

    @Test
    open fun testValidJsonWithExtraFields(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"value" : "value", "val" : "bad_json" } """)
        }.let { call ->
            assertEquals(extraFieldResult, call.response.status())
        }
    }

    @Test
    open fun testSendJsonStringServer() = testApplication {
        routing {
            get("/") {
                call.respond("abc")
            }
        }
        install(ContentNegotiation) {
            clearIgnoredTypes()
            register(ContentType.Application.Json, converter)
        }

        client.get("/").let { response ->
            assertEquals("\"abc\"", response.bodyAsText())
        }
    }

    @Test
    open fun testReceiveJsonStringServer() = testApplication {
        install(ContentNegotiation) {
            clearIgnoredTypes()
            register(ContentType.Application.Json, converter)
        }
        routing {
            post("/") {
                val request = call.receive<String>()
                assertEquals("abc", request)
                call.respond("OK")
            }
        }

        client.post("/") {
            setBody(TextContent("\"abc\"", ContentType.Application.Json))
        }.let { response ->
            assertEquals("\"OK\"", response.bodyAsText())
        }
    }

    @Test
    open fun testReceiveJsonStringClient() = testApplication {
        routing {
            get("/") {
                call.respond(TextContent("\"abc\"", ContentType.Application.Json))
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                clearIgnoredTypes()
                register(ContentType.Application.Json, converter)
            }
        }.get("/").let { response ->
            assertEquals("abc", response.body())
        }
    }

    @Test
    open fun testSendJsonStringClient() = testApplication {
        routing {
            post("/") {
                val request = call.receive<String>()
                assertEquals("\"abc\"", request)
                call.respond("OK")
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                clearIgnoredTypes()
                register(ContentType.Application.Json, converter)
            }
        }.post("/") {
            contentType(ContentType.Application.Json)
            setBody("abc")
        }.let { response ->
            assertEquals("OK", response.bodyAsText())
        }
    }

    @Test
    open fun testJsonNullServer() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }
        routing {
            post("/") {
                val request = call.receiveNullable<Wrapper?>()
                assertEquals(null, request)
                call.respondNullable(request)
            }
        }

        client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("null")
        }.let { response ->
            assertEquals("null", response.bodyAsText())
        }
    }

    @Test
    open fun testJsonNullClient() = testApplication {
        routing {
            post("/") {
                val request = call.receive<String>()
                assertEquals("null", request)
                call.respond(TextContent("null", ContentType.Application.Json))
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                register(ContentType.Application.Json, converter)
            }
        }.post("/") {
            val data: Wrapper? = null
            contentType(ContentType.Application.Json)
            setBody(data)
        }.let { response ->
            assertEquals(null, response.body<Wrapper?>())
        }
    }

    @Test
    fun testNoCharsetIsAdded() = testApplication {
        routing {
            post("/") {
                assertNull(call.request.contentType().charset())
                call.respond("OK")
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                register(ContentType("application", "json-patch+json"), converter)
            }
        }.post("/") {
            val data: Wrapper? = null
            contentType(ContentType("application", "json-patch+json"))
            setBody(data)
        }.let {
            assertEquals("OK", it.bodyAsText())
        }
    }

    @Test
    fun testRespondWithTypeInfoAny() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        fun buildResponse(): Any = Wrapper("abc")

        routing {
            get("/") {
                call.respond(buildResponse())
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                register(ContentType.Application.Json, converter)
            }
        }.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"value":"abc"}""", response.bodyAsText())
        }
    }

    @Test
    fun testRespondSealedWithTypeInfoAny() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        fun buildResponse(): Any = DataType("abc")

        routing {
            get("/") {
                call.respond(buildResponse())
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                register(ContentType.Application.Json, converter)
            }
        }.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"value":"abc"}""", response.bodyAsText())
        }
    }

    @Test
    fun testNoDuplicatedHeaders() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                register(ContentType.Application.Json, converter)
            }
        }.get {
            header(HttpHeaders.Accept, "application/json")
        }.let { response ->
            response.request.headers.forEach { _, values ->
                assertEquals(1, values.size)
            }
        }
    }

    @Test
    open fun testRespondNestedSealedWithTypeInfoAny() = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        fun buildResponse(): Any = SealedWrapper(Sealed.A("abc"))

        routing {
            get("/") {
                call.respond(buildResponse())
            }
        }

        createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                register(ContentType.Application.Json, converter)
            }
        }.get("/").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"value":{"value":"abc"}}""", response.bodyAsText())
        }
    }
}

@Serializable
sealed class Data

@Serializable
class DataType(val value: String) : Data()

@Serializable
class SealedWrapper(val value: Sealed)

@Serializable
sealed class Sealed {
    @Serializable
    data class A(val value: String) : Sealed()

    @Serializable
    data class B(val value: String) : Sealed()
}
