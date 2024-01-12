/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.websocket

import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.debug.junit5.*
import java.nio.*
import java.nio.charset.*
import java.time.*
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.test.*

@Suppress("DEPRECATION")
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

    @Test
    fun testSingleEcho() {
        withTestApplication {
            application.install(WebSockets)
            application.routing {
                webSocketRaw("/echo") {
                    incoming.consumeEach { frame ->
                        if (!frame.frameType.controlFrame) {
                            send(frame.copy())
                            flush()
                            cancel()
                        }
                    }
                }
            }

            handleWebSocket("/echo") {
                setBody(hex("""0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f""".trimHex()))
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))
                assertEquals("810548656c6c6f", hex(call.response.byteContent!!))
            }
        }
    }

    @Test
    fun testJsonConverter() {
        withTestApplication {
            application.install(WebSockets) {
                contentConverter = customContentConverter
            }

            application.routing {
                webSocket("/echo") {
                    val data = receiveDeserialized<Data>()
                    sendSerialized(data)
                    outgoing.send(Frame.Close())
                }
            }

            val jsonData = "[hello]"

            handleWebSocketConversation("/echo") { incoming, outgoing ->
                outgoing.send(Frame.Text(jsonData))

                val frame = incoming.receive() as Frame.Text
                assertEquals(FrameType.TEXT, frame.frameType)
                assertEquals(jsonData, frame.readText())
            }
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
                outgoing.send(Frame.Close())
            }
        }

        val jsonData = "[hello]"

        createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }.webSocket("/echo") {
            outgoing.send(Frame.Text(jsonData))

            val frame = incoming.receive() as Frame.Text
            assertEquals(FrameType.TEXT, frame.frameType)
            assertEquals(jsonData, frame.readText())
        }
    }

    @Test
    fun testSerializationWithNoConverter() {
        withTestApplication {
            application.install(WebSockets) {}

            application.routing {
                webSocket("/echo") {
                    assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                        receiveDeserialized<Data>()
                    }
                    assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                        sendSerialized(Data("hello"))
                    }
                    outgoing.send(Frame.Close())
                }
            }

            val jsonData = "[hello]"
            val sendBuffer = ByteBuffer.allocate(jsonData.length + 100)

            Serializer().apply {
                enqueue(Frame.Text(jsonData))
                serialize(sendBuffer)
            }

            val conversation = Job()

            handleWebSocket("/echo") {
                bodyChannel = writer {
                    channel.writeFully(sendBuffer.array())
                    channel.flush()
                    conversation.join()
                }.channel
            }.let {
                runBlocking {
                    withTimeout(Duration.ofSeconds(10).toMillis()) {}
                }
            }
        }
    }

    @Test
    fun testDeserializationWithOnClosedChannel() {
        withTestApplication {
            application.install(WebSockets) {
                contentConverter = customContentConverter
            }

            application.routing {
                webSocket("/echo") {
                    assertFailsWith<ClosedReceiveChannelException> {
                        receiveDeserialized<Data>()
                    }
                    outgoing.send(Frame.Close())
                }
            }
            val sendBuffer = ByteBuffer.allocate(100)

            Serializer().apply {
                enqueue(Frame.Text(""))
                serialize(sendBuffer)
            }

            val conversation = Job()

            handleWebSocket("/echo") {
                bodyChannel = writer {
                    channel.writeFully(sendBuffer.array())
                    channel.flush()
                    conversation.join()
                }.channel
            }.let {
                runBlocking {
                    withTimeout(Duration.ofSeconds(10).toMillis()) {}
                }
            }
        }
    }

    @Test
    fun testFrameSize() {
        withTestApplication {
            application.install(WebSockets)

            application.routing {
                webSocketRaw("/echo") {
                    outgoing.send(Frame.Text("+".repeat(0xc123)))
                    outgoing.send(Frame.Close())
                }
                webSocketRaw("/receiveSize") {
                    val frame = incoming.receive()
                    val bytes = buildPacket {
                        writeInt(frame.buffer.remaining())
                    }

                    outgoing.send(Frame.Binary(true, bytes))
                    outgoing.send(Frame.Close())
                }
            }

            handleWebSocket("/echo") {
                setBody(byteArrayOf())
            }.let { call ->
                assertEquals("817ec123", hex(call.response.byteContent!!.take(4).toByteArray()))
                call.response.awaitWebSocket(Duration.ofSeconds(10))
            }

            handleWebSocket("/receiveSize") {
                setBody(hex("0x81 0x7e 0xcd 0xef".trimHex()) + "+".repeat(0xcdef).toByteArray())
            }.let { call ->
                assertEquals("82040000cdef", hex(call.response.byteContent!!.take(6).toByteArray()))
                call.response.awaitWebSocket(Duration.ofSeconds(10))
            }
        }
    }

    @Test
    fun testMasking() {
        withTestApplication {
            application.install(WebSockets)
            application.routing {
                webSocketRaw("/echo") {
                    masking = true

                    incoming.consumeEach { frame ->
                        if (!frame.frameType.controlFrame) {
                            assertEquals("Hello", frame.buffer.copy().array().toString(Charsets.UTF_8))
                            send(frame.copy())
                            flush()
                            cancel()
                        }
                    }
                }
            }

            handleWebSocket("/echo") {
                setBody(hex("""0x81 0x85 0x37 0xfa 0x21 0x3d 0x7f 0x9f 0x4d 0x51 0x58""".trimHex()))
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))

                val bb = ByteBuffer.wrap(call.response.byteContent!!)
                assertEquals(11, bb.remaining())

                val parser = FrameParser()
                parser.frame(bb)

                assertTrue { parser.bodyReady }
                assertTrue { parser.mask }
                val key = parser.maskKey!!

                val collector = SimpleFrameCollector()
                collector.start(parser.length.toInt(), bb)

                assertFalse { collector.hasRemaining }

                assertEquals("Hello", collector.take(key).copy().array().toString(Charsets.UTF_8))
            }
        }
    }

    @Test
    fun testSendClose() {
        withTestApplication {
            application.install(WebSockets)

            application.routing {
                webSocket("/echo") {
                    incoming.consumeEach { }
                }
            }

            handleWebSocket("/echo") {
                setBody(hex("""0x88 0x02 0xe8 0x03""".trimHex()))
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))
                assertEquals("0x88 0x02 0xe8 0x03".trimHex(), hex(call.response.byteContent!!))
            }
        }
    }

    @Test
    fun testParameters() {
        withTestApplication {
            application.install(WebSockets)

            application.routing {
                webSocket("/{p}") {
                    val frame = Frame.Text(call.parameters["p"] ?: "null")
                    outgoing.send(frame)
                }
            }

            handleWebSocket("/aaa") {
            }.let { call ->
                runBlocking {
                    val channel = call.response.websocketChannel()!!

                    val parser = FrameParser()
                    val content = channel.readRemaining().readBytes()
                    check(content.isNotEmpty()) { "Content it empty." }

                    val buffer = ByteBuffer.wrap(content)
                    parser.frame(buffer)

                    assertEquals(FrameType.TEXT, parser.frameType)
                    assertTrue { parser.bodyReady }

                    val bytes = ByteArray(parser.length.toInt())
                    buffer.get(bytes)

                    assertEquals("aaa", bytes.toString(Charsets.ISO_8859_1))
                }
            }
        }
    }

    @Test
    fun testBigFrame() {
        val content = ByteArray(20 * 1024 * 1024)
        Random().nextBytes(content)

        val sendBuffer = ByteBuffer.allocate(content.size + 100)

        Serializer().apply {
            enqueue(Frame.Binary(true, ByteBuffer.wrap(content)))
            serialize(sendBuffer)

            sendBuffer.flip()
        }

        withTestApplication {
            application.install(WebSockets)

            application.routing {
                webSocket("/") {
                    val frame = incoming.receive()
                    val copied = frame.copy()
                    outgoing.send(copied)

                    flush()
                }
            }

            val conversation = Job()

            handleWebSocket("/") {
                bodyChannel = writer {
                    channel.writeFully(sendBuffer.moveToByteArray())
                    channel.flush()
                    conversation.join()
                }.channel
            }.let { call ->
                runBlocking {
                    withTimeout(Duration.ofSeconds(10).toMillis()) {
                        val reader = WebSocketReader(
                            call.response.websocketChannel()!!,
                            coroutineContext,
                            Int.MAX_VALUE.toLong()
                        )

                        val frame = reader.incoming.receive()
                        val receivedContent = frame.buffer.moveToByteArray()

                        conversation.complete()

                        assertEquals(FrameType.BINARY, frame.frameType)
                        assertEquals(content.size, receivedContent.size)

                        assertTrue { receivedContent.contentEquals(content) }
                    }

                    call.response.awaitWebSocket(Duration.ofSeconds(10))
                }
            }
        }
    }

    @Test
    fun testFragmentation() {
        val sendBuffer = ByteBuffer.allocate(1024)

        Serializer().apply {
            enqueue(Frame.Text(false, ByteBuffer.wrap("ABC".toByteArray())))
            enqueue(Frame.Ping(ByteBuffer.wrap("ping".toByteArray()))) // ping could be interleaved
            enqueue(Frame.Text(false, ByteBuffer.wrap("12".toByteArray())))
            enqueue(Frame.Text(true, ByteBuffer.wrap("3".toByteArray())))
            enqueue(Frame.Close())
            serialize(sendBuffer)

            sendBuffer.flip()
        }

        withTestApplication {
            application.install(WebSockets)

            var receivedText: String? = null
            application.routing {
                webSocket("/") {
                    val frame = incoming.receive()

                    if (frame is Frame.Text) {
                        receivedText = frame.readText()
                    } else {
                        fail()
                    }
                }
            }

            handleWebSocket("/") {
                setBody(sendBuffer.array())
            }.let { call ->
                call.response.awaitWebSocket(Duration.ofSeconds(10))

                assertEquals("ABC123", receivedText)
            }
        }
    }

    @Test
    fun testMaxSize() {
        val sendBuffer = ByteBuffer.allocate(5 * 1024)

        Serializer().apply {
            enqueue(Frame.Binary(true, ByteArray(1024)))
            enqueue(Frame.Close())
            serialize(sendBuffer)
            sendBuffer.flip()
        }

        withTestApplication {
            application.install(WebSockets) {
                maxFrameSize = 1023
            }

            var exception: Throwable? = null
            val started = Job()
            val executed = Job()
            application.routing {
                webSocket("/") {
                    try {
                        started.complete()
                        incoming.receive()
                    } catch (cause: Throwable) {
                        exception = cause
                    } finally {
                        executed.complete()
                    }
                }
            }

            handleWebSocket("/") {
                bodyChannel = writer {
                    started.join()
                    channel.writeFully(sendBuffer)
                }.channel
            }.let { call ->
                validateCloseWithBigFrame(call)
                runBlocking {
                    executed.join()
                }

                assertTrue("Expected FrameTooBigException, but found $exception") {
                    exception is FrameTooBigException
                }
            }
        }
    }

    @Test
    fun testFragmentationMaxSize() {
        val sendBuffer = ByteBuffer.allocate(5 * 1024)

        Serializer().apply {
            repeat(3) {
                enqueue(Frame.Binary(false, ByteArray(1024)))
            }
            enqueue(Frame.Binary(true, ByteArray(1024)))
            enqueue(Frame.Close())
            serialize(sendBuffer)
            sendBuffer.flip()
        }

        withTestApplication {
            application.install(WebSockets) {
                maxFrameSize = 1025
            }

            var exception: Throwable? = null
            application.routing {
                webSocket("/") {
                    try {
                        incoming.receive()
                    } catch (cause: Throwable) {
                        exception = cause
                    }
                }
            }

            handleWebSocket("/") {
                setBody(sendBuffer.array())
            }.let { call ->
                validateCloseWithBigFrame(call)
                assertTrue { exception is FrameTooBigException }
            }
        }
    }

    @Test
    fun testFragmentationMaxSizeBound() {
        val sendBuffer = ByteBuffer.allocate(5 * 1024)

        Serializer().apply {
            enqueue(Frame.Binary(false, ByteArray(1024)))
            enqueue(Frame.Binary(true, ByteArray(1024)))
            enqueue(Frame.Close())
            serialize(sendBuffer)
            sendBuffer.flip()
        }

        withTestApplication {
            application.install(WebSockets) {
                maxFrameSize = 1025
            }

            var exception: Throwable? = null
            val executed = Job()
            application.routing {
                webSocket("/") {
                    try {
                        incoming.receive()
                    } catch (cause: Throwable) {
                        exception = cause
                    } finally {
                        executed.complete()
                    }
                }
            }

            handleWebSocket("/") {
                setBody(sendBuffer.array())
            }.let { call ->
                validateCloseWithBigFrame(call)
                runBlocking {
                    executed.join()
                }
                assertTrue { exception is FrameTooBigException }
            }
        }
    }

    @Test
    fun testConversation() {
        withTestApplication {
            application.install(WebSockets)

            val received = arrayListOf<String>()
            application.routing {
                webSocket("/echo") {
                    try {
                        while (true) {
                            val text = (incoming.receive() as Frame.Text).readText()
                            received += text
                            outgoing.send(Frame.Text(text))
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

            handleWebSocketConversation("/echo") { incoming, outgoing ->
                val textMessages = listOf("HELLO", "WORLD")
                for (msg in textMessages) {
                    outgoing.send(Frame.Text(msg))
                    assertEquals(msg, (incoming.receive() as Frame.Text).readText())
                }
                assertEquals(textMessages, received)
            }
        }
    }

    @Test
    fun testConversationWithInterceptors() {
        withTestApplication {
            application.install(WebSockets)

            application.intercept(ApplicationCallPipeline.Monitoring) {
                coroutineScope {
                    proceed()
                }
            }

            val received = arrayListOf<String>()
            application.routing {
                webSocket("/echo") {
                    try {
                        while (true) {
                            val text = (incoming.receive() as Frame.Text).readText()
                            received += text
                            outgoing.send(Frame.Text(text))
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

            handleWebSocketConversation("/echo") { incoming, outgoing ->
                val textMessages = listOf("HELLO", "WORLD")
                for (msg in textMessages) {
                    outgoing.send(Frame.Text(msg))
                    assertEquals(msg, (incoming.receive() as Frame.Text).readText())
                }
                assertEquals(textMessages, received)
            }
        }
    }

    @Test
    fun testFlushClosed(): Unit = withTestApplication {
        application.install(WebSockets)

        val session = CompletableDeferred<Unit>()
        application.routing {
            webSocket("/close/me") {
                try {
                    close()
                    delay(1)
                    flush()
                    session.complete(Unit)
                } catch (cause: Throwable) {
                    session.completeExceptionally(cause)
                    throw cause
                }
            }
        }

        handleWebSocketConversation("/close/me") { incoming, _ ->
            assertTrue(incoming.receive() is Frame.Close)
            assertNull(incoming.receiveCatching().getOrNull())
        }
        runBlocking {
            session.await()
        }
    }

    private fun String.trimHex() = replace("\\s+".toRegex(), "").replace("0x", "")

    private fun validateCloseWithBigFrame(call: TestApplicationCall) = runBlocking {
        withTimeout(Duration.ofSeconds(10).toMillis()) {
            val reader = WebSocketReader(
                call.response.websocketChannel()!!,
                coroutineContext,
                Int.MAX_VALUE.toLong()
            )

            val frame = reader.incoming.receive()
            call.response.awaitWebSocket(Duration.ofSeconds(10))

            assertTrue("Expected Frame.Close, but found $frame") { frame is Frame.Close }
            val reason = (frame as Frame.Close).readReason()
            assertEquals(CloseReason.Codes.TOO_BIG.code, reason?.code)
        }
    }
}
