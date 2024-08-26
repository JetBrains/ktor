/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import kotlin.coroutines.*
import kotlin.test.*
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class TestApplicationTestJvm {

    @Test
    fun testDefaultConfigDoesNotLoad() = testApplication {
        application {
            val config = environment.config
            routing {
                get("a") {
                    call.respond(config.propertyOrNull("ktor.test").toString())
                }
            }
        }

        val response = client.get("a")
        assertEquals("null", response.bodyAsText())
    }

    @Test
    fun testWebSockets() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/echo") {
                for (message in incoming) {
                    outgoing.send(message)
                }
            }
        }

        val client = createClient { install(ClientWebSockets) }
        client.ws("/echo") {
            outgoing.send(Frame.Text("Hello"))
            repeat(100) {
                val frame = incoming.receive() as Frame.Text
                assertEquals("Hello" + ".".repeat(it), frame.readText())
                outgoing.send(Frame.Text(frame.readText() + "."))
            }
        }
    }

    @Test
    fun testWebSocketWithError() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Setup) {
                throw RuntimeException()
            }

            install(WebSockets)

            routing {
                route("/") {
                    webSocket {
                        incoming.consumeEach {}
                    }
                }
            }
        }

        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets) {}
        }

        assertFails {
            client.webSocketSession {}
        }
    }

    @Test
    fun testCustomEnvironmentKeepsDefaultProperties() = testApplication {
        environment {
            config = ApplicationConfig("application-custom.conf")
        }
        routing {
            val config = environment.config
            get("a") {
                call.respond(config.property("ktor.test").getString())
            }
        }

        val response = client.get("a")
        assertEquals("another_test_value", response.bodyAsText())
    }

    @Test
    fun testExplicitDefaultConfig() = testApplication {
        environment {
            config = ConfigLoader.load()
        }
        routing {
            val config = environment.config
            get {
                call.respond(config.property("ktor.test").getString())
            }
        }

        val response = client.get("/")
        assertEquals("test_value", response.bodyAsText())
    }

    @Test
    fun testCustomConfig() = testApplication {
        environment {
            config = ApplicationConfig("application-custom.conf")
        }
        routing {
            val config = environment.config
            get {
                call.respond(config.property("ktor.test").getString())
            }
        }

        val response = client.get("/")
        assertEquals("another_test_value", response.bodyAsText())
    }

    @Test
    fun testCustomYamlConfig() = testApplication {
        environment {
            config = ApplicationConfig("application-custom.yaml")
        }
        routing {
            val config = environment.config
            get {
                call.respond(config.property("ktor.test").getString())
            }
        }

        val response = client.get("/")
        assertEquals("another_test_value", response.bodyAsText())
    }

    @Test
    fun testConfigLoadsModules() = testApplication {
        environment {
            config = ApplicationConfig("application-with-modules.conf")
        }

        val response = client.get("/")
        assertEquals("OK FROM MODULE", response.bodyAsText())
    }

    @Test
    fun testExternalServicesCustomConfig() = testApplication {
        environment {
            config = ApplicationConfig("application-custom.conf")
        }
        externalServices {
            hosts("http://www.google.com") {
                val config = environment.config
                routing {
                    get {
                        val configValue = config.propertyOrNull("ktor.test")?.getString() ?: "no value"
                        call.respond(configValue)
                    }
                }
            }
        }

        val external = client.get("http://www.google.com")
        assertEquals("another_test_value", external.bodyAsText())
    }

    @Test
    fun testModuleWithLaunch() = testApplication {
        var error: Throwable? = null
        val exceptionHandler: CoroutineContext = object : CoroutineExceptionHandler {
            override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler.Key
            override fun handleException(context: CoroutineContext, exception: Throwable) {
                error = exception
            }
        }
        testApplicationProperties {
            parentCoroutineContext = exceptionHandler
        }
        application {
            launch {
                val byteArrayInputStream = ByteArrayOutputStream()
                val objectOutputStream = ObjectOutputStream(byteArrayInputStream)
                objectOutputStream.writeObject(TestClass(123))
                objectOutputStream.flush()
                objectOutputStream.close()

                val ois = TestObjectInputStream(ByteArrayInputStream(byteArrayInputStream.toByteArray()))
                val test = ois.readObject()
                test as TestClass
            }
        }
        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/")
        Thread.sleep(3000)
        assertNull(error)
    }

    @Test
    fun testMultipleParallelWebSocketsRequests() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/") {
                send(incoming.receive())
            }
        }

        val client = createClient {
            install(ClientWebSockets)
        }
        coroutineScope {
            val jobs = (1..100).map {
                async {
                    client.ws("/") {
                        send(Frame.Text("test"))
                        assertEquals("test", (incoming.receive() as Frame.Text).readText())
                    }
                }
            }
            jobs.forEach { it.join() }
        }
    }

    @Test
    fun testRetrievingPluginInstance() = testApplication {
        install(MyCalculatorPlugin)
        application {
            val result = plugin(MyCalculatorPlugin).add(1, 2)
            assertEquals(3, result)
        }
    }

    @Test
    fun testCanPassCoroutineContextFromOutsideWithWS() = runBlocking(MyElement("test")) {
        testApplication(coroutineContext) {
            install(WebSockets)
            routing {
                webSocket("ws") {
                    assertEquals("test", (incoming.receive() as Frame.Text).readText())
                    outgoing.send(Frame.Text(coroutineContext[MyElement]!!.data))
                }
            }
            val client = createClient {
                install(ClientWebSockets)
            }

            client.webSocket("ws") {
                outgoing.send(Frame.Text(coroutineContext[MyElement]!!.data))
                assertEquals("test", (incoming.receive() as Frame.Text).readText())
            }
        }
    }

    class MyElement(val data: String) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = MyElement

        companion object : CoroutineContext.Key<MyElement>

        override fun toString(): String = "=====$data====="
    }

    fun Application.module() {
        routing {
            get { call.respond("OK FROM MODULE") }
        }
    }

    @Test
    fun testWebSocketConnectionFailed() = testApplication {
        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }
        val error = assertFailsWith<IllegalStateException> {
            client.webSocket("/ws") { }
        }
        assertEquals("WebSocket connection failed", error.message)
    }

    private fun testSocketTimeoutWrite(timeout: Long, expectException: Boolean) = testApplication {
        routing {
            post {
                call.respond(HttpStatusCode.OK, call.request.receiveChannel().readRemaining().toString())
            }
        }

        val clientWithTimeout = createClient {
            install(HttpTimeout) {
                socketTimeoutMillis = timeout
            }
        }

        val body = object : OutgoingContent.WriteChannelContent() {
            override suspend fun writeTo(channel: ByteWriteChannel) {
                channel.writeByteArray("Hello".toByteArray())
                channel.flush()
                delay(300)
                channel.writeByteArray("World".toByteArray())
                channel.flush()
            }
        }

        if (expectException) {
            assertFailsWith<SocketTimeoutException> {
                clientWithTimeout.post("/") {
                    setBody(body)
                }
            }
        } else {
            clientWithTimeout.post("/") {
                setBody(body)
            }.apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }

    @Test
    fun testSocketTimeoutWriteElapsed() = testSocketTimeoutWrite(100, true)

    @Test
    fun testSocketTimeoutWriteNotElapsed() = testSocketTimeoutWrite(1000, false)
}

class TestClass(val value: Int) : Serializable

class TestObjectInputStream(input: InputStream) : ObjectInputStream(input) {
    override fun resolveClass(desc: ObjectStreamClass?): Class<*> {
        val name = desc?.name
        val loader = Thread.currentThread().contextClassLoader

        return try {
            Class.forName(name, false, loader)
        } catch (e: ClassNotFoundException) {
            super.resolveClass(desc)
        }
    }
}

class MyCalculatorPlugin {
    class Configuration
    companion object Plugin : BaseApplicationPlugin<ApplicationCallPipeline, Configuration, MyCalculatorPlugin> {
        override val key = AttributeKey<MyCalculatorPlugin>("MyCalculatorPlugin")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): MyCalculatorPlugin {
            return MyCalculatorPlugin()
        }
    }

    fun add(x: Int, y: Int): Int {
        return x + y
    }
}
