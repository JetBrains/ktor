/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.test.*

abstract class HttpClientTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: EmbeddedServer<*, *> = embeddedServer(CIO, serverPort) {
        routing {
            get("/empty") {
                call.respondText("")
            }
            get("/hello") {
                call.respondText("hello")
            }
            post("/echo") {
                val text = call.receiveText()
                call.respondText(text)
            }

            route("/sse") {
                val messages = Channel<String>()
                get("/stream") {
                    val body = object : OutgoingContent.WriteChannelContent() {
                        override val contentType: ContentType
                            get() = ContentType.Text.EventStream

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            for (message in messages) {
                                channel.writeStringUtf8(message)
                                channel.flush()
                            }
                        }
                    }

                    call.respond(body)
                }
                post("/next") {
                    val message = call.receiveText()
                    messages.send(message)
                    call.respond("OK")
                }
                get("/done") {
                    messages.close()
                    call.respond("OK")
                }
            }
        }
    }

    @Test
    fun testClientSSE() = runBlocking {
        val client = HttpClient(factory) {
            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }

        client.prepareGet("http://localhost:$serverPort/sse/stream").execute {
            val body = it.bodyAsChannel()

            repeat(10) {
                client.post("http://localhost:$serverPort/sse/next") {
                    setBody(TextContent("hello\n", ContentType.Text.Plain))
                }

                assertEquals("hello", body.readUTF8Line())
            }

            client.get("http://localhost:$serverPort/sse/done")
            assertEquals(null, body.readUTF8Line())
        }
    }

    @Test
    fun testWithNoParentJob() {
        val block = suspend {
            val client = HttpClient(factory)
            val statement = client.prepareGet("http://localhost:$serverPort/hello")
            assertEquals("hello", statement.execute().bodyAsText())
        }

        val latch = ArrayBlockingQueue<Result<Unit>>(1)

        block.startCoroutine(
            object : Continuation<Unit> {
                override val context: CoroutineContext
                    get() = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    latch.put(result)
                }
            },
        )

        latch.take().exceptionOrNull()?.let { throw it }
    }

    @Test
    fun configCopiesOldPluginsAndInterceptors() {
        val customPluginKey = AttributeKey<Boolean>("customPlugin")
        val anotherCustomPluginKey = AttributeKey<Boolean>("anotherCustomPlugin")

        val originalClient = HttpClient(factory) {
            useDefaultTransformers = false

            install(DefaultRequest) {
                port = serverPort
                url.path("empty")
            }
            install("customPlugin") {
                attributes.put(customPluginKey, true)
            }
        }

        // check everything was installed in original
        val originalRequest = runBlocking {
            originalClient.request(HttpRequestBuilder())
        }.request
        assertEquals("/empty", originalRequest.url.fullPath)

        assertTrue(originalClient.attributes.contains(customPluginKey), "no custom plugin installed")

        // create a new client, copying the original, with:
        // - a reconfigured DefaultRequest
        // - a new custom plugin
        val newClient = originalClient.config {
            install(DefaultRequest) {
                port = serverPort
                url.path("hello")
            }
            install("anotherCustomPlugin") {
                attributes.put(anotherCustomPluginKey, true)
            }
        }

        // check the custom plugin remained installed
        // and that we override the DefaultRequest
        val newRequest = runBlocking {
            newClient.request(HttpRequestBuilder())
        }.request
        assertEquals("/hello", newRequest.url.fullPath)

        assertTrue(newClient.attributes.contains(customPluginKey), "no custom plugin installed")

        // check the new custom plugin is there too
        assertTrue(newClient.attributes.contains(anotherCustomPluginKey), "no other custom plugin installed")
    }

    private class SendException : RuntimeException("Error on write")
}
