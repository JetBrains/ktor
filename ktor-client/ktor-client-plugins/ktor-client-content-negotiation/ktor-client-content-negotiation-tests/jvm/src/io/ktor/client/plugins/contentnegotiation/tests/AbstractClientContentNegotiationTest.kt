/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.plugins.contentnegotiation.tests

import com.fasterxml.jackson.annotation.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlin.test.*

/** Base class for [ContentNegotiation] tests. */

abstract class AbstractClientContentNegotiationTest : TestWithKtor() {
    private val widget = Widget("Foo", 1000, listOf("a", "b", "c"))
    protected val users = listOf(
        User("x", 10),
        User("y", 45)
    )

    override val server: EmbeddedServer<*, *> = embeddedServer(io.ktor.server.cio.CIO, serverPort) {
        install(WebSockets)
        routing {
            createRoutes(this)
        }
    }

    protected abstract val defaultContentType: ContentType
    protected abstract val customContentType: ContentType
    protected abstract val webSocketsConverter: WebsocketContentConverter

    @OptIn(InternalSerializationApi::class)
    private suspend inline fun <reified T : Any> ApplicationCall.respond(
        responseJson: String,
        contentType: ContentType
    ): Unit = respond(responseJson, contentType, T::class.serializer())

    protected open suspend fun <T : Any> ApplicationCall.respond(
        responseJson: String,
        contentType: ContentType,
        serializer: KSerializer<T>,
    ) {
        respondText(responseJson, contentType)
    }

    protected open suspend fun ApplicationCall.respondWithRequestBody(contentType: ContentType) {
        respondText(receiveText(), contentType)
    }

    protected abstract fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType)
    protected fun TestClientBuilder<*>.configureClient(
        block: ContentNegotiationConfig.() -> Unit = {}
    ) {
        config {
            install(ContentNegotiation) {
                configureContentNegotiation(defaultContentType)
                block()
            }
            install(io.ktor.client.plugins.websocket.WebSockets) {
                contentConverter = webSocketsConverter
            }
        }
    }

    protected open fun createRoutes(route: Route): Unit = with(route) {
        post("/echo") {
            call.respondWithRequestBody(call.request.contentType())
        }
        post("/widget") {
            call.respondWithRequestBody(defaultContentType)
        }
        get("/users") {
            call.respond(
                """{"ok":true,"result":[{"name":"x","age":10},{"name":"y","age":45}]}""",
                defaultContentType,
                Response.serializer(ListSerializer(User.serializer()))
            )
        }
        get("/users-x") { // route for testing custom content type, namely "application/x-${contentSubtype}"
            call.respond(
                """{"ok":true,"result":[{"name":"x","age":10},{"name":"y","age":45}]}""",
                customContentType,
                Response.serializer(ListSerializer(User.serializer()))
            )
        }
        post("/post-x") {
            require(call.request.contentType().withoutParameters() == customContentType) {
                "Request body content type should be $customContentType"
            }

            call.respondWithRequestBody(defaultContentType)
        }
        post("/null") {
            assertEquals("null", call.receiveText())
            call.respondText("null", defaultContentType)
        }
        webSocket("ws") {
            for (frame in incoming) {
                outgoing.send(frame)
            }
        }
    }

    @Test
    fun testEmptyBody(): Unit = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    respond(
                        request.body.toByteReadPacket().readText(),
                        headers = headersOf("X-ContentType", request.body.contentType.toString())
                    )
                }
            }
            defaultRequest {
                contentType(defaultContentType)
            }
            install(ContentNegotiation) {
                configureContentNegotiation(defaultContentType)
            }
        }

        test { client ->
            val response: HttpResponse = client.get("https://test.com")
            assertEquals("", response.bodyAsText())
            assertEquals("null", response.headers["X-ContentType"])
        }
    }

    @Test
    fun testSerializeSimple(): Unit = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val result = client.post {
                setBody(widget)
                url(path = "/widget", port = serverPort)
                contentType(defaultContentType)
            }.body<Widget>()

            assertEquals(widget, result)
        }
    }

    @Test
    open fun testSerializeFailureHasOriginalCauseMessage(): Unit = testWithEngine(CIO) {
        configureClient()

        @Serializable
        data class WrongWidget(
            val wrongField: String,
            val name: String,
            val value: Int,
            val tags: List<String> = emptyList()
        )

        test { client ->
            val cause = kotlin.test.assertFailsWith<JsonConvertException> {
                client.post {
                    setBody(widget)
                    url(path = "/widget", port = serverPort)
                    contentType(defaultContentType)
                }.body<WrongWidget>()
            }
            assertTrue(cause.message!!.contains("wrongField"))
        }
    }

    @Test
    open fun testSerializeNull(): Unit = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val data: Widget? = null
            val result = client.post {
                url(path = "/null", port = serverPort)
                contentType(defaultContentType)
                setBody(data)
            }.body<Widget?>()

            assertEquals(null, result)
        }
    }

    @Test
    open fun testSerializeNested(): Unit = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val result = client.get { url(path = "/users", port = serverPort) }.body<Response<List<User>>>()

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }
    }

    @Test
    fun testCustomContentTypes(): Unit = testWithEngine(CIO) {
        configureClient {
            configureContentNegotiation(customContentType)
        }

        test { client ->
            val result = client.get { url(path = "/users-x", port = serverPort) }.body<Response<List<User>>>()

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }

        test { client ->
            client.prepareGet { url(path = "/users-x", port = serverPort) }.execute { response ->
                val result = response.body<Response<List<User>>>()

                assertTrue(result.ok)
                assertNotNull(result.result)
                assertEquals(users, result.result)

                assertEquals(customContentType, response.contentType()?.withoutParameters())
            }
        }

        test { client ->
            val payload = User("name1", 99)

            val result = client.post {
                url(path = "/post-x", port = serverPort)
                setBody(payload)
                contentType(customContentType)
            }.body<User>()

            assertEquals(payload, result)
        }
    }

    @Test
    fun testCustomContentTypesMultiple(): Unit = testWithEngine(CIO) {
        configureClient {
            configureContentNegotiation(customContentType)
        }

        test { client ->
            val payload = User("name2", 98)

            val result = client.post {
                url(path = "/post-x", port = serverPort)
                setBody(payload)
                contentType(customContentType)
            }.body<User>()

            assertEquals(payload, result)
        }
    }

    @Test
    fun testCustomContentTypesWildcard(): Unit = testWithEngine(CIO) {
        configureClient {
            configureContentNegotiation(customContentType)
        }

        test { client ->
            client.prepareGet { url(path = "/users-x", port = serverPort) }.execute { response ->
                val result = response.body<Response<List<User>>>()

                assertTrue(result.ok)
                assertNotNull(result.result)
                assertEquals(users, result.result)

                // defaultContentType is registered first on server so it should win
                // since Accept header consist of the wildcard
                assertEquals(defaultContentType, response.contentType()?.withoutParameters())
            }
        }

        test { client ->
            val payload = User("name3", 97)

            val result = client.post {
                url(path = "/post-x", port = serverPort)
                setBody(payload)
                contentType(customContentType) // custom content type should match the wildcard
            }.body<User>()

            assertEquals(payload, result)
        }
    }

    @Test
    open fun testGeneric(): Unit = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val result = client.post {
                url(path = "/echo", port = serverPort)
                contentType(defaultContentType)
                setBody(Response(true, users))
            }.body<Response<List<User>>>()

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }
    }

    @Test
    open fun testSealed(): Unit = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val result = client.post {
                url(path = "/echo", port = serverPort)
                contentType(defaultContentType)
                setBody(listOf(TestSealed.A("A"), TestSealed.B("B")))
            }.body<List<TestSealed>>()

            assertEquals(listOf(TestSealed.A("A"), TestSealed.B("B")), result)
        }
    }

    @Test
    fun testSerializeWebsocket() = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val session = client.webSocketSession { url(path = "/ws", port = serverPort) }
            session.sendSerialized(User("user1", 23))
            val user1 = session.receiveDeserialized<User>()

            session.send(session.converter!!.serialize(User("user2", 32)))
            val frame = session.incoming.receive()
            val user2 = session.converter!!.deserialize<User>(frame)

            session.close()
            assertEquals("user1", user1.name)
            assertEquals(23, user1.age)
            assertEquals("user2", user2.name)
            assertEquals(32, user2.age)
        }
    }

    @Serializable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    sealed class TestSealed {
        @Serializable
        data class A(val valueA: String) : TestSealed()

        @Serializable
        data class B(val valueB: String) : TestSealed()
    }

    @Serializable
    data class Response<T>(
        val ok: Boolean,
        @Contextual
        val result: T?
    )

    @Serializable
    data class Widget(
        val name: String,
        val value: Int,
        val tags: List<String> = emptyList()
    )

    @Serializable
    data class User(
        val name: String,
        val age: Int
    )
}
