/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.websocket

import io.ktor.utils.io.*
import io.ktor.utils.io.locks.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.test.*
import kotlinx.io.*
import kotlin.reflect.*
import kotlin.test.*

@OptIn(DelicateCoroutinesApi::class)
class RawWebSocketTest {
    private lateinit var parent: CompletableJob
    private lateinit var client2server: ByteChannel
    private lateinit var server2client: ByteChannel

    private lateinit var server: WebSocketSession

    private lateinit var client: WebSocketSession

    private val errors = mutableListOf<Throwable>()

    @OptIn(InternalAPI::class)
    private val errorsLock = SynchronizedObject()

    @OptIn(InternalAPI::class)
    private fun collectUnhandledException(error: Throwable) {
        synchronized(errorsLock) { errors.add(error) }
    }

    @AfterTest
    fun _verifyErrors() {
        if (errors.isEmpty()) return

        val error = IllegalStateException(
            "There were ${errors.size} unhandled errors during running test (suppressed)"
        )

        errors.forEach {
            error.addSuppressed(it)
        }
        error.printStackTrace()
        throw error // suppressed exceptions print wrong in idea
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, cause ->
        if (cause !is CancellationException && cause !is IOException) {
            collectUnhandledException(cause)
        }
    }

    @BeforeTest
    fun prepare() {
        parent = Job()
        client2server = ByteChannel()
        server2client = ByteChannel()

        server = RawWebSocket(client2server, server2client, coroutineContext = parent + exceptionHandler)
        client = RawWebSocket(server2client, client2server, coroutineContext = parent + exceptionHandler)
    }

    @AfterTest
    fun cleanup() {
        server.cancel()
        client.cancel()
        client2server.cancel()
        server2client.cancel()
        parent.cancel()
    }

    @Test
    fun smokeTest() = runTest {
        val text = "smoke"
        client.send(Frame.Ping(text.encodeToByteArray()))

        val receivedPing = server.incoming.receive()
        assertEquals(FrameType.PING, receivedPing.frameType)
        var bytes = receivedPing.readBytes()
        assertEquals(text, bytes.decodeToString(0, 0 + bytes.size))

        server.send(Frame.Pong(text.encodeToByteArray()))
        val receivedPong = client.incoming.receive()
        assertEquals(FrameType.PONG, receivedPong.frameType)
        bytes = receivedPong.readBytes()
        assertEquals(text, bytes.decodeToString(0, 0 + bytes.size))

        client.cancel()
        server.cancel()

        ensureCompletion()
    }

    @Test
    fun testServerIncomingDisconnected() = runTest {
        client2server.close()
        assertNull(server.incoming.receiveCatching().getOrNull())
        server.outgoing.send(Frame.Close())

        client.incoming.receiveCatching().getOrNull() as Frame.Close
        client.cancel()

        ensureCompletion()
    }

    @Ignore // KTOR-7318 Investigate raw websocket disconnect hanging
    @Test
    fun testServerIncomingConnectionLoss() = runTest {
        client2server.close(PlannedIOException())
        ensureCompletion(allowedExceptionsFromIncoming = listOf(IOException::class))
    }

    @Test
    fun testCloseSequenceInitiatedByClient() = runTest {
        val text = "content"

        client.send(Frame.Text(text))
        val textFrame = server.incoming.receive() as Frame.Text
        assertEquals(text, textFrame.readText())

        client.close()

        val clientCloseFrame = server.incoming.receive() as Frame.Close
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, ""), clientCloseFrame.readReason())

        server.close(clientCloseFrame.readReason()!!)

        val serverCloseFrame = client.incoming.receive()
        assertTrue { serverCloseFrame is Frame.Close }

        ensureCompletion()
    }

    @Test
    fun testSendToClosed() = runTest {
        cancelAtIncomingEnd(server)
        client.close()
        ensureCompletion()

        assertFailsWith<CancellationException> {
            server.outgoing.send(Frame.Close())
        }
        assertFailsWith<ClosedSendChannelException> {
            client.outgoing.send(Frame.Close())
        }

        Unit
    }

    @Test
    fun testCloseSequenceInitiatedByClientNoMessages() = runTest {
        cancelAtIncomingEnd(server)
        client.close()
        ensureCompletion()
    }

    @Test
    fun testParentCancellation() = runTest {
        parent.cancel()
        ensureCompletion()
    }

    @Test
    fun testServerTerminate() = runTest {
        cancelAtIncomingEnd(client)
        server.cancel()
        ensureCompletion()
    }

    private suspend fun ensureCompletion(allowedExceptionsFromIncoming: List<KClass<out Throwable>> = emptyList()) {
        parent.complete()
        parent.join()

        assertTrue("client -> server channel should be closed") { client2server.isClosedForRead }
        assertTrue("client -> server channel should be closed") { client2server.isClosedForWrite }

        assertTrue("server -> client channel should be closed") { server2client.isClosedForRead }
        assertTrue("server -> client channel should be closed") { server2client.isClosedForWrite }

        try {
            server.incoming.consumeEach {}
        } catch (_: CancellationException) {
        } catch (other: Throwable) {
            other.assertOnOf(allowedExceptionsFromIncoming)
        }

        try {
            client.incoming.consumeEach {}
        } catch (_: CancellationException) {
        } catch (other: Throwable) {
            other.assertOnOf(allowedExceptionsFromIncoming)
        }

        assertTrue("client incoming should be closed") { client.incoming.isClosedForReceive }
        assertTrue("server incoming should be closed") { server.incoming.isClosedForReceive }

        assertTrue("client outgoing should be closed") { client.outgoing.isClosedForSend }
        assertTrue("server outgoing should be closed") { server.outgoing.isClosedForSend }
    }

    private fun Throwable.assertOnOf(
        exceptions: Collection<KClass<out Throwable>>
    ) {
        if (exceptions.none { it.isInstance(this) }) {
            throw this
        }
    }

    private fun CoroutineScope.cancelAtIncomingEnd(side: WebSocketSession) {
        launch {
            side.incoming.consumeEach {}
            side.cancel()
        }
    }

    private class PlannedIOException : IOException("Connection loss.")
}
