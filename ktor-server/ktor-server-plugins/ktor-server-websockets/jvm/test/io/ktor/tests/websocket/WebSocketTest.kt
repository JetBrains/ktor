/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.cio.*
import io.ktor.junit.coroutines.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@CoroutinesTimeout(30_000)
class WebSocketTest {

    class Data(val string: String)

    private val customContentConverter = object : WebsocketContentConverter {
        override suspend fun serialize(
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): Frame {
            if (value !is Data) return Frame.Text("")
            return Frame.Text("[${value.string}]")
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any {
            if (typeInfo.type != Data::class) {
                return Data("")
            }
            if (content !is Frame.Text) {
                return Data("")
            }
            return Data(content.readText().removeSurrounding("[", "]"))
        }

        override fun isApplicable(frame: Frame): Boolean {
            return frame is Frame.Text
        }
    }

    private fun ApplicationTestBuilder.createWebSocketsClient(): HttpClient = createClient {
        install(io.ktor.client.plugins.websocket.WebSockets)
    }

    @Test
    fun testSingleEcho() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/echo") {
                val frame = incoming.receive()
                send(frame.copy())
            }
        }

        createWebSocketsClient().webSocket("/echo") {
            send("Hello")
            assertEquals("Hello", receiveText())
        }
    }

    @Test
    fun testJsonConverter() = testApplication {
        install(WebSockets) {
            contentConverter = customContentConverter
        }

        routing {
            webSocket("/echo") {
                val data = receiveDeserialized<Data>()
                sendSerialized(data)
            }
        }

        val jsonData = "[hello]"

        createWebSocketsClient().webSocket("/echo") {
            send(jsonData)
            assertEquals(jsonData, receiveText())
        }
    }

    @Test
    fun testJsonConverterWithExplicitTypeInfo() = testApplication {
        install(WebSockets) {
            contentConverter = customContentConverter
        }

        routing {
            webSocket("/echo") {
                val data = receiveDeserialized<Data>(typeInfo<Data>())
                sendSerialized(data, typeInfo<Data>())
            }
        }

        val jsonData = "[hello]"

        createWebSocketsClient().webSocket("/echo") {
            send(jsonData)
            assertEquals(jsonData, receiveText())
        }
    }

    @Test
    fun testSerializationWithNoConverter() = testApplication {
        install(WebSockets)

        val result = DeferredResult()
        routing {
            webSocket("/echo") {
                withDeferredResult(result) {
                    assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                        receiveDeserialized<Data>()
                    }
                    assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                        sendSerialized(Data("hello"))
                    }
                }
            }
        }

        createWebSocketsClient().webSocket("/echo") {}

        result.await().getOrThrow()
    }

    @Test
    fun testDeserializationWithOnClosedChannel() = testApplication {
        install(WebSockets) {
            contentConverter = customContentConverter
        }

        val result = DeferredResult()
        routing {
            webSocket("/echo") {
                withDeferredResult(result) {
                    assertFailsWith<ClosedReceiveChannelException> {
                        receiveDeserialized<Data>()
                    }
                }
            }
        }

        createWebSocketsClient().webSocket("/echo") {
            close()
        }

        result.await().getOrThrow()
    }

    @Test
    fun testFrameSize() = testApplication {
        install(WebSockets)

        routing {
            webSocket("/echo") {
                send("+".repeat(0xc123))
            }

            webSocket("/receiveSize") {
                val frame = incoming.receive()
                val bytes = buildPacket { writeInt(frame.data.size) }.readByteArray()

                send(bytes)
            }
        }

        val client = createWebSocketsClient()

        client.webSocket("/echo") {
            val frame = incoming.receive()
            assertEquals(0xc123, frame.data.size)
        }

        client.webSocket("/receiveSize") {
            send("+".repeat(0xcdef))
            assertEquals("0000cdef", hex(incoming.receive().readBytes()))
        }
    }

    @Test
    fun testSendClose() = testApplication {
        install(WebSockets)

        routing {
            webSocket("/") {}
        }

        var receivedClose = false
        createWebSocketsClient().webSocketRaw(path = "/") {
            send(Frame.Close())
            receivedClose = incoming.receive() is Frame.Close
        }

        assertTrue(receivedClose, "Frame.Close was not received by the client")
    }

    @Test
    fun testParameters() = testApplication {
        install(WebSockets)

        routing {
            webSocket("/{p}") {
                send(call.parameters["p"] ?: "null")
            }
        }

        createWebSocketsClient().webSocket("/aaa") {
            assertEquals("aaa", receiveText())
        }
    }

    @Test
    fun testBigFrame() = testApplication {
        install(WebSockets)

        routing {
            webSocket("/echo") {
                val frame = incoming.receive()
                send(frame.copy())
            }
        }

        val content = Random.nextBytes(ByteArray(20 * 1024 * 1024))
        var receivedContent = byteArrayOf()

        createWebSocketsClient().webSocket("/echo") {
            withTimeout(10.seconds) {
                send(content)

                val frame = incoming.receive()
                assertEquals(FrameType.BINARY, frame.frameType)

                receivedContent = frame.readBytes()
            }
        }

        assertEquals(content.size, receivedContent.size)
        assertContentEquals(content, receivedContent)
    }

    @Test
    fun testFragmentation() = testApplication {
        install(WebSockets)

        var receivedText: String? = null
        val executed = Job()
        routing {
            webSocket("/") {
                receivedText = receiveText()
                executed.complete()
            }
        }

        createWebSocketsClient().webSocket("/") {
            send(Frame.Text(false, "ABC".toByteArray()))
            send(Frame.Ping("ping".toByteArray())) // ping could be interleaved
            send(Frame.Text(false, "12".toByteArray()))
            send(Frame.Text(true, "3".toByteArray()))
        }

        executed.join()
        assertEquals("ABC123", receivedText)
    }

    @Test
    fun testMaxSize() = testApplication {
        install(WebSockets) {
            maxFrameSize = 1023
        }

        val started = Job()
        val result = DeferredResult()
        routing {
            webSocket("/") {
                withDeferredResult(result) {
                    started.complete()
                    incoming.receive()
                }
            }
        }

        var clientClosedProperly = false
        createWebSocketsClient().webSocketRaw {
            started.join()
            send(ByteArray(1024))

            validateCloseWithBigFrame()
            clientClosedProperly = true
        }

        val exception = result.await().exceptionOrNull()
        assertTrue(clientClosedProperly, "Client wasn't closed properly")
        assertTrue("Expected FrameTooBigException, but found $exception") {
            exception is FrameTooBigException
        }
    }

    @Test
    fun testFragmentationMaxSize() = testApplication {
        install(WebSockets) {
            maxFrameSize = 1025
        }

        val result = DeferredResult()
        routing {
            webSocket("/") {
                withDeferredResult(result) {
                    incoming.receive()
                }
            }
        }

        var clientClosedProperly = false
        createWebSocketsClient().webSocketRaw(path = "/") {
            repeat(3) {
                send(Frame.Binary(false, ByteArray(1024)))
            }
            send(Frame.Binary(true, ByteArray(1024)))

            validateCloseWithBigFrame()
            clientClosedProperly = true
        }

        val exception = result.await().exceptionOrNull()
        assertTrue(clientClosedProperly, "Client wasn't closed properly")
        assertTrue("Expected FrameTooBigException, but found $exception") {
            exception is FrameTooBigException
        }
    }

    @Test
    fun testFragmentationMaxSizeBound() = testApplication {
        install(WebSockets) {
            maxFrameSize = 1025
        }

        val result = DeferredResult()
        routing {
            webSocket("/") {
                withDeferredResult(result) {
                    incoming.receive()
                }
            }
        }

        var clientClosedProperly = false
        createWebSocketsClient().webSocketRaw(path = "/") {
            send(Frame.Binary(false, ByteArray(1024)))
            send(Frame.Binary(true, ByteArray(1024)))

            validateCloseWithBigFrame()
            clientClosedProperly = true
        }

        val exception = result.await().exceptionOrNull()
        assertTrue(clientClosedProperly, "Client wasn't closed properly")
        assertTrue { exception is FrameTooBigException }
    }

    @Test
    fun testConversation() = testApplication {
        install(WebSockets)

        val received = arrayListOf<String>()
        routing {
            webSocket("/echo") {
                try {
                    while (true) {
                        val text = receiveText()
                        received += text
                        send(text)
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Do nothing!
                } catch (e: CancellationException) {
                    // Do nothing!
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }

        val textMessages = listOf("HELLO", "WORLD")
        createWebSocketsClient().webSocket("/echo") {
            for (msg in textMessages) {
                send(msg)
                assertEquals(msg, receiveText())
            }
        }

        assertEquals(textMessages, received)
    }

    @Test
    fun testConversationWithInterceptors() = testApplication {
        install(WebSockets)

        application {
            intercept(ApplicationCallPipeline.Monitoring) {
                coroutineScope {
                    proceed()
                }
            }
        }

        val received = arrayListOf<String>()
        routing {
            webSocket("/echo") {
                try {
                    while (true) {
                        val text = receiveText()
                        received += text
                        send(text)
                    }
                } catch (e: ClosedReceiveChannelException) {
                    // Do nothing!
                } catch (e: CancellationException) {
                    // Do nothing!
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }

        val textMessages = listOf("HELLO", "WORLD")
        createWebSocketsClient().webSocket("/echo") {
            for (msg in textMessages) {
                send(msg)
                assertEquals(msg, receiveText())
            }
        }

        assertEquals(textMessages, received)
    }

    @Test
    fun testFlushClosed() = testApplication {
        install(WebSockets)

        val session = CompletableDeferred<Result<Unit>>()
        routing {
            webSocket("/close/me") {
                withDeferredResult(session) {
                    close()
                    delay(1)
                    flush()
                }
            }
        }

        var callClosedProperly = false
        createWebSocketsClient().webSocketRaw(path = "/close/me") {
            assertTrue(incoming.receive() is Frame.Close)
            assertNull(incoming.receiveCatching().getOrNull())
            callClosedProperly = true
        }

        session.await().getOrThrow()
        assertTrue(callClosedProperly, "Client wasn't closed properly")
    }

    private suspend fun WebSocketSession.validateCloseWithBigFrame() {
        withTimeout(10.seconds) {
            val frame = incoming.receive()

            assertTrue("Expected Frame.Close, but found $frame") { frame is Frame.Close }
            val reason = (frame as Frame.Close).readReason()
            assertEquals(CloseReason.Codes.TOO_BIG.code, reason?.code)
        }
    }

    private suspend fun WebSocketSession.receiveText(): String {
        val frame = incoming.receive()
        assertTrue(frame is Frame.Text)
        return frame.readText()
    }
}

private typealias DeferredResult = CompletableDeferred<Result<Unit>>

private fun DeferredResult(): DeferredResult = CompletableDeferred()

private inline fun withDeferredResult(deferred: DeferredResult, block: () -> Unit) {
    try {
        block()
        deferred.complete(Result.success(Unit))
    } catch (cause: Throwable) {
        deferred.complete(Result.failure(cause))
        throw cause
    }
}
