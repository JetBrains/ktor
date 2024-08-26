/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.random.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
abstract class WebSocketEngineSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {
    private val errors = mutableListOf<Throwable>()
    override val timeout = 30.seconds

    override fun plugins(application: Application, routingConfig: Route.() -> Unit) {
        application.install(WebSockets)
        super.plugins(application, routingConfig)
    }

    @Test
    fun testWebSocketDisconnectDuringConsuming() = runTest {
        val closeReasonJob = Job()
        val contextJob = Job()

        createAndStartServer {
            webSocket("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                launch {
                    try {
                        closeReason.await()
                    } finally {
                        closeReasonJob.complete()
                    }
                }

                incoming.consumeEach {}
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(100)
            }
        }

        withTimeout(5000) {
            closeReasonJob.join()
            contextJob.join()
        }

        result.await()
    }

    @Test
    fun testWebSocketDisconnectDuringSending() = runTest {
        val closeReasonJob = Job()
        val contextJob = Job()

        createAndStartServer {
            webSocket("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                launch {
                    try {
                        closeReason.await()
                    } finally {
                        closeReasonJob.complete()
                    }
                }

                while (true) {
                    send(Frame.Text("a".repeat(2000)))
                    delay(100)
                }
            }
        }

        val result = async(Dispatchers.Unconfined) {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(150)
            }
        }

        withTimeout(5000) {
            closeReasonJob.join()
            contextJob.join()
        }

        result.await()
    }

    @Test
    fun testWebSocketDisconnectDuringDowntime() = runTest {
        val closeReasonJob = Job()
        val contextJob = Job()

        createAndStartServer {
            webSocket("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                launch {
                    try {
                        closeReason.await()
                    } finally {
                        closeReasonJob.complete()
                    }
                }

                delay(10000)
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(100)
            }
        }

        withTimeout(5000) {
            closeReasonJob.join()
            contextJob.join()
        }

        result.await()

        delay(5000)
    }

    @Test
    fun testRawWebSocketDisconnectDuringConsuming() = runTest {
        val contextJob = Job()

        createAndStartServer {
            webSocketRaw("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                incoming.consumeEach {}
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(1000)
            }
        }

        withTimeout(5000) {
            contextJob.join()
        }

        result.await()
    }

    @Ignore // fails process on native
    @Test
    fun testRawWebSocketDisconnectDuringSending() = runTest {
        val contextJob = Job()

        createAndStartServer {
            webSocketRaw("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                while (true) {
                    send(Frame.Text("a".repeat(2000)))
                    delay(100)
                }
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(100)
            }
        }

        withTimeout(5000) {
            contextJob.join()
        }

        result.await()
    }

    @Ignore // For now we assume that without any network interactions the socket will remain open.
    @Test
    fun testRawWebSocketDisconnectDuringDowntime() = runTest {
        val contextJob = Job()

        createAndStartServer {
            webSocketRaw("/") {
                coroutineContext[Job]!!.invokeOnCompletion {
                    contextJob.complete()
                }

                delay(10000)
            }
        }

        val result = async {
            useSocket {
                negotiateHttpWebSocket()
                input.cancel()
                delay(10000)
            }
        }

        withTimeout(5000) {
            contextJob.join()
        }

        result.await()
    }

    @Test
    open fun testWebSocketGenericSequence() = runTest {
        val collected = Channel<String>(Channel.UNLIMITED)

        createAndStartServer {
            webSocket("/") {
                try {
                    val frame = incoming.receive()
                    assertIs<Frame.Text>(frame)
                    collected.send(frame.readText())
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                // text message with content "Hello"
                writeHex("0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f")
                flush()

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }

        assertEquals("Hello", collected.receive())
    }

    @Test
    fun testWebSocketPingPong() = runTest {
        createAndStartServer {
            webSocket("/") {
                timeoutMillis = 120.seconds.inWholeMilliseconds
                pingIntervalMillis = 50.milliseconds.inWholeMilliseconds

                try {
                    incoming.consumeEach {
                    }
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            for (i in 1..5) {
                val frame = input.readFrame(Long.MAX_VALUE, 0)

                assertEquals(FrameType.PING, frame.frameType)
                assertEquals(true, frame.fin)
                assertTrue(frame.data.isNotEmpty())

                output.writeFrame(Frame.Pong(frame.data), false)
                output.flush()
            }

            output.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testReceiveMessages() = runTest {
        val count = 125
        val template = (1..count).joinToString("") { (it and 0x0f).toString(16) }
        val bytes = template.toByteArray()

        val collected = Channel<String>(Channel.UNLIMITED)

        createAndStartServer {
            webSocket("/") {
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            collected.send(frame.readText())
                        }
                    }
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                    collected.send(t.toString())
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                for (i in 1..count) {
                    writeHex("0x81")
                    writeByte(i.toByte())
                    writeFully(bytes, 0, i)
                    flush()
                }

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }

        for (i in 1..count) {
            val expected = template.substring(0, i)
            assertEquals(expected, collected.receive())
        }

        assertNull(collected.tryReceive().getOrNull())
    }

    @Test
    open fun testConnectionWithContentType() = runTest {
        val count = 5
        val template = (1..count).joinToString("") { (it and 0x0f).toString(16) }
        val bytes = template.toByteArray()

        val collected = Channel<String>(Channel.UNLIMITED)

        createAndStartServer {
            webSocket("/") {
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            collected.send(frame.readText())
                        }
                    }
                } catch (cancelled: CancellationException) {
                } catch (cause: Throwable) {
                    errors.add(cause)
                    collected.send(cause.toString())
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket(listOf("Content-Type" to "application/json"))

            output.apply {
                for (i in 1..count) {
                    writeHex("0x81")
                    writeByte(i.toByte())
                    writeFully(bytes, 0, i)
                    flush()
                }

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }

        for (i in 1..count) {
            val expected = template.substring(0, i)
            assertEquals(expected, collected.receive())
        }

        assertNull(collected.tryReceive().getOrNull())
    }

    @Test
    fun testProduceMessages() = runTest {
        val count = 125
        val template = (1..count).joinToString("") { (it and 0x0f).toString(16) }

        createAndStartServer {
            webSocket("/") {
                for (i in 1..count) {
                    send(Frame.Text(template.substring(0, i)))
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            input.apply {
                for (i in 1..count) {
                    val f = readFrame(Long.MAX_VALUE, 0)
                    assertEquals(FrameType.TEXT, f.frameType)
                    assertEquals(template.substring(0, i), ByteReadPacket(f.data).readText(Charsets.ISO_8859_1))
                }
            }

            output.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testBigFrame() = runTest {
        val content = ByteArray(20 * 1024 * 1024)
        Random.nextBytes(content)

        createAndStartServer {
            webSocket("/") {
                val f = incoming.receive()

                val copied = f.copy()
                outgoing.send(copied)

                flush()
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                writeFrame(Frame.Binary(true, content), false)
                flush()
            }

            input.apply {
                val frame = readFrame(Long.MAX_VALUE, 0)

                assertEquals(FrameType.BINARY, frame.frameType)
                assertEquals(content.size, frame.data.size)
                assertContentEquals(content, frame.data)
            }

            output.apply {
                writeFrame(Frame.Close(), false)
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testALotOfFrames() = runTest {
        val expectedCount = 100000L

        createAndStartServer {
            webSocket("/") {
                try {
                    var counter = 1L
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val numberRead = frame.readText().toLong()
                            assertEquals(counter, numberRead, "Wrong packet received")

                            counter++
                        }
                    }

                    assertEquals(expectedCount, counter - 1, "Not all frames received")
                } catch (cancelled: CancellationException) {
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                for (i in 1L..expectedCount) {
                    writeFrame(Frame.Text(true, i.toString().toByteArray()), false)
                }
                writeFrame(Frame.Close(), false)
                flush()
            }

            assertCloseFrame()
        }
    }

    @Test
    fun testServerClosingFirst() = runTest {
        createAndStartServer {
            webSocket("/") {
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "test"))
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            // it should be close frame immediately
            assertCloseFrame(CloseReason.Codes.TRY_AGAIN_LATER.code, replyCloseFrame = false)

            // we should be able to write close frame back
            output.apply {
                writeFrame(Frame.Close(), false)
                flush()
            }
        }
    }

    @Test
    open fun testClientClosingFirst() = runTest {
        val deferred = CompletableDeferred<Unit>()

        createAndStartServer {
            webSocket("/") {
                try {
                    assertNull(incoming.receiveCatching().getOrNull(), "Incoming channel should be closed")
                    assertFailsWith<CancellationException>("Outgoing channel should be closed properly") {
                        repeat(10) {
                            // we need this loop because the outgoing is not closed immediately
                            outgoing.send(Frame.Text("Should not be sent."))
                            delay(100)
                        }
                    }
                } catch (failed: Throwable) {
                    deferred.completeExceptionally(failed)
                } finally {
                    deferred.complete(Unit)
                }
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                writeFrame(Frame.Close(CloseReason(CloseReason.Codes.GOING_AWAY, "Completed.")), false)
                flush()
            }

            val reply = input.readFrame(Long.MAX_VALUE, 0) as Frame.Close
            val reason = reply.readReason()
            assertNotNull(reason)

            deferred.await()
        }
    }

    @Test
    open fun testFragmentedFlagsFromTheFirstFrame() = runTest {
        val first = CompletableDeferred<Frame.Text>()
        val second = CompletableDeferred<Frame.Text>()
        createAndStartServer {
            webSocket("/") {
                val frame = incoming.receive()
                assertIs<Frame.Text>(frame)
                first.complete(frame)

                val frame2 = incoming.receive()
                assertIs<Frame.Text>(frame2)
                second.complete(frame2)
            }
        }

        useSocket {
            negotiateHttpWebSocket()

            output.apply {
                repeat(2) {
                    writeFrameTest(Frame.Text(false, "Hello".toByteArray(), true, false, false), false)
                    writeFrameTest(Frame.Text(true, ", World".toByteArray(), false, false, false), false, opcode = 0)
                }
                writeFrameTest(Frame.Close(), false)
                flush()
            }
        }

        fun checkFrame(frame: Frame) {
            assertIs<Frame.Text>(frame)
            assertTrue(frame.fin)
            assertTrue(frame.rsv1)
            assertFalse(frame.rsv2)
            assertFalse(frame.rsv3)

            assertEquals("Hello, World", frame.readText())
        }

        checkFrame(first.await())
        checkFrame(second.await())
    }

    private suspend fun Connection.negotiateHttpWebSocket(additionalHeader: List<Pair<String, String>> = emptyList()) {
        // send upgrade request
        output.apply {
            writeFully(
                """
                    GET / HTTP/1.1
                    Host: localhost:$port
                    Upgrade: websocket
                    Connection: Upgrade
                    Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                    Origin: http://localhost:$port
                    Sec-WebSocket-Protocol: chat
                    Sec-WebSocket-Version: 13
                    ${additionalHeader.joinToString("\n") { "${it.first}: ${it.second}" }}
                """.trimIndent().trim().replace("\n", "\r\n").encodeToByteArray()
            )
            writeFully("\r\n\r\n".encodeToByteArray())
            flush()
        }

        val status = input.parseStatus()
        assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

        val headers = input.parseHeaders()
        assertEquals("Upgrade", headers[HttpHeaders.Connection])
        assertEquals("websocket", headers[HttpHeaders.Upgrade])
    }

    private suspend fun Connection.assertCloseFrame(
        closeCode: Short = CloseReason.Codes.NORMAL.code,
        replyCloseFrame: Boolean = true
    ) {
        loop@
        while (true) when (val frame = input.readFrame(Long.MAX_VALUE, 0)) {
            is Frame.Ping -> continue@loop
            is Frame.Close -> {
                assertEquals(closeCode, frame.readReason()?.code)
                if (replyCloseFrame) socket.close()
                break@loop
            }

            else -> fail("Unexpected frame $frame: \n${hex(frame.data)}")
        }
    }

    private suspend fun ByteWriteChannel.writeHex(hex: String) = writeFully(fromHexDump(hex))

    private fun fromHexDump(hex: String) = hex(hex.replace("0x", "").replace("\\s+".toRegex(), ""))

    //
    private suspend fun ByteReadChannel.parseStatus(): HttpStatusCode {
        val line = readLineISOCrLf()

        assertTrue(line.startsWith("HTTP/1.1"), "status line should start with HTTP version, actual content: $line")

        val statusCodeAndMessage = line.removePrefix("HTTP/1.1").trimStart()
        val statusCodeString = statusCodeAndMessage.takeWhile(Char::isDigit)
        val message = statusCodeAndMessage.removePrefix(statusCodeString).trimStart()

        return HttpStatusCode(statusCodeString.toInt(), message)
    }

    private suspend fun ByteReadChannel.parseHeaders(): Headers {
        val builder = HeadersBuilder()

        while (true) {
            val line = readLineISOCrLf()
            if (line.isEmpty()) {
                return builder.build()
            }

            val (name, value) = line.split(":").map(String::trim)
            builder.append(name, value)
        }
    }

    private suspend fun ByteReadChannel.readLineISOCrLf(): String {
        val sb = StringBuilder(256)

        while (true) when (val rc = readByte().toInt()) {
            -1, 0x0a -> return sb.toString()
            0x0d -> {}
            else -> sb.append(rc.toChar())
        }
    }

    private suspend inline fun useSocket(block: Connection.() -> Unit) {
        SelectorManager().use {
            aSocket(it).tcp().connect("localhost", port) {
                noDelay = true
                socketTimeout = 4.minutes.inWholeMilliseconds
            }.use {
                val connection = it.connection()
                try {
                    block(connection)
                    // for native, output should be closed explicitly
                    connection.output.flushAndClose()
                } catch (cause: Throwable) {
                    throw cause
                }
            }
        }
    }
}

internal suspend fun ByteWriteChannel.writeFrameTest(frame: Frame, masking: Boolean, opcode: Int? = null) {
    val length = frame.data.size

    val flagsAndOpcode = frame.fin.flagAt(7) or
        frame.rsv1.flagAt(6) or
        frame.rsv2.flagAt(5) or
        frame.rsv3.flagAt(4) or
        (opcode ?: frame.frameType.opcode)

    writeByte(flagsAndOpcode.toByte())

    val formattedLength = when {
        length < 126 -> length
        length <= 0xffff -> 126
        else -> 127
    }

    val maskAndLength = masking.flagAt(7) or formattedLength

    writeByte(maskAndLength.toByte())

    when (formattedLength) {
        126 -> writeShort(length.toShort())
        127 -> writeLong(length.toLong())
    }

    val data = ByteReadPacket(frame.data)

    val maskedData = when (masking) {
        true -> {
            val maskKey = Random.nextInt()
            writeInt(maskKey)
            data.mask(maskKey)
        }

        false -> data
    }
    writePacket(maskedData)
}

internal fun Boolean.flagAt(at: Int) = if (this) 1 shl at else 0

private fun Source.mask(maskKey: Int): Source = withMemory(4) { maskMemory ->
    maskMemory.storeIntAt(0, maskKey)
    buildPacket {
        repeat(remaining.toInt()) { i ->
            writeByte((readByte().toInt() xor (maskMemory[i % 4].toInt())).toByte())
        }
    }
}
