/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.http.*
import io.ktor.server.cio.backend.*
import io.ktor.server.cio.internal.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.*
import org.junit.jupiter.api.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.test.Test
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalAPI::class)
class ServerPipelineTest : CoroutineScope {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(8)

    private val job: CompletableJob = SupervisorJob()
    private var name: CoroutineName = CoroutineName("PipelineTest")

    override val coroutineContext: CoroutineContext by lazy {
        (dispatcher as CoroutineContext) + (job as CoroutineContext.Element) + (name as AbstractCoroutineContextElement)
    }

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        name = CoroutineName("PipelineTest:${testInfo.testMethod.map { it.name }.orElse("")}")
    }

    @AfterEach
    fun cleanup() {
        job.cancel()
        runBlocking {
            job.join()
        }
    }

    @Test
    fun testSmoke(): Unit = runBlocking {
        val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
        val job = startServerConnectionPipeline(connection, timeout = Duration.INFINITE) {
            error("Shouldn't reach here")
        }

        job.cancel()
    }

    @Test
    fun testSingleRequest(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestsReceived = ArrayList<String>()

        val connection = ServerIncomingConnection(input, output, null, null)
        startServerConnectionPipeline(connection, timeout = Duration.INFINITE) { request ->
            requestsReceived += request.uri.toString()
            assertEquals("/", request.uri.toString())
            assertEquals("GET", request.method.value)
            assertEquals("HTTP/1.1", request.version.toString())
            assertEquals("close", request.headers[HttpHeaders.Connection].toString())

            assertNull(upgraded)

            request.release()

            output.writeStringUtf8("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
        }

        input.writeStringUtf8("GET / HTTP/1.1\r\nConnection: close\r\n\r\n")
        input.flush()

        assertEquals("HTTP/1.1 200 OK", output.readUTF8Line())
        assertEquals("Connection: close", output.readUTF8Line())
        assertEquals("", output.readUTF8Line())
        assertEquals("/", requestsReceived.single())

        input.close()
        output.readRemaining().discard()
    }

    @Test
    fun testSingleRequestUpgradeWithoutUpgrade(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestsReceived = ArrayList<String>()

        val connection = ServerIncomingConnection(input, output, null, null)
        startServerConnectionPipeline(connection, timeout = Duration.INFINITE) { request ->
            requestsReceived += request.uri.toString()
            assertEquals("/", request.uri.toString())
            assertEquals("GET", request.method.value)
            assertEquals("HTTP/1.1", request.version.toString())
            assertEquals("Upgrade", request.headers[HttpHeaders.Connection].toString())

            assertNotNull(upgraded)
            upgraded!!.complete(true)

            request.release()

            output.writeStringUtf8("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n")
        }

        input.writeStringUtf8("GET / HTTP/1.1\r\nUpgrade: test\r\nConnection: Upgrade\r\n\r\n")
        input.flush()

        assertEquals("HTTP/1.1 200 OK", output.readUTF8Line())
        assertEquals("Connection: close", output.readUTF8Line())
        assertEquals("", output.readUTF8Line())
        assertEquals("/", requestsReceived.single())

        input.close()
        output.readRemaining().discard()
    }

    @Test
    fun testSingleRequestUpgradeNoTimeout(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestsReceived = ArrayList<String>()
        val latch = Job()

        val connection = ServerIncomingConnection(input, output, null, null)
        startServerConnectionPipeline(connection, timeout = 1.milliseconds) { request ->
            requestsReceived += request.uri.toString()
            assertEquals("/", request.uri.toString())
            assertEquals("GET", request.method.value)
            assertEquals("HTTP/1.1", request.version.toString())
            assertEquals("Upgrade", request.headers[HttpHeaders.Connection].toString())

            assertNotNull(upgraded)
            upgraded!!.complete(true)

            request.release()

            output.writeStringUtf8("HTTP/1.1 101 Switching\r\nUpgrade: test\r\nConnection: Upgrade\r\n\r\n")
            output.flush()

            latch.join()
        }

        input.writeStringUtf8("GET / HTTP/1.1\r\nUpgrade: test\r\nConnection: Upgrade\r\n\r\n")
        input.flush()

        assertEquals("HTTP/1.1 101 Switching", output.readUTF8Line())
        assertEquals("Upgrade: test", output.readUTF8Line())
        assertEquals("Connection: Upgrade", output.readUTF8Line())
        assertEquals("", output.readUTF8Line())
        assertEquals("/", requestsReceived.single())

        delay(100)
        latch.complete()

        input.close()
        output.readRemaining().discard()
    }

    @Test
    fun testPipelineIdleTimeoutNoRequests(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val connection = ServerIncomingConnection(input, output, null, null)
        supervisorScope {
            startServerConnectionPipeline(connection, Duration.ZERO) {
                error("Shouldn't reach here")
            }

            // it's important to close the joint channel as it happens in real networks
            // this is really only a test specific thing
            launch(CoroutineName("IO helper")) {
                try {
                    output.discard()
                } finally {
                    input.close()
                }
            }
        }
    }

    @Test
    fun testPipelineIdleTimeoutAfterRequests(): Unit = runBlocking(coroutineContext) {
        val input = ByteChannel()
        val output = ByteChannel()

        val requestHandled = Job()

        val connection = ServerIncomingConnection(input, output, null, null)
        supervisorScope {
            startServerConnectionPipeline(connection, timeout = 100.milliseconds) { request ->
                requestHandled.complete()
                request.release()
                input.cancel()
                output.close()
            }

            // send a single request
            input.writeStringUtf8("GET / HTTP/1.1\r\nConnection: keep-alive\r\n\r\n")
            input.flush()

            // after processing the request, the idle timeout machinery should cancel all the stuff
            requestHandled.join()

            // it's important to close the joint channel as it happens in real networks
            // this is really only a test specific thing
            launch(CoroutineName("IO helper")) {
                try {
                    output.discard()
                } finally {
                    input.close()
                }
            }
        }
    }

    @Test
    fun testParentJobAndTimerCancellation() {
        val l = CountDownLatch(1)

        val root = launch(coroutineContext) {
            val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
            startServerConnectionPipeline(connection, timeout = Duration.INFINITE) {
                error("Shouldn't reach here")
            }
            l.countDown()
        }

        l.await()
        Thread.sleep(100)

        runBlocking {
            root.cancel()

            root.join()
        }
    }

    @Test
    fun testParentJobAndTimeoutCancellation(): Unit = runBlocking(coroutineContext) {
        val l = Job()

        val root = launch {
            val connection = ServerIncomingConnection(ByteChannel(), ByteChannel(), null, null)
            startServerConnectionPipeline(connection, timeout = 10.milliseconds) {
                error("Shouldn't reach here")
            }
            l.complete()
        }

        l.join()
        delay(100) // we need this delay because launching a coroutine takes time

        delay(1)
        root.cancelAndJoin()
    }
}
