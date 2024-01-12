/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugin.tracing.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.coroutines.*
import io.ktor.utils.io.*

class TracingWrapperTest {
    @Test
    fun testGet() = runBlocking {
        val tracer = TestTracer()
        HttpClient(TracingWrapper(CIO, tracer)).use { client ->
            val result: String = client.get("http://www.google.com/")
            assertNotNull(result)
        }

        synchronized(tracer) {
            assertEquals(1, tracer.requestWillBeSentCalls.size)
            assertEquals(1, tracer.responseHeadersReceivedCalls.size)
            assertEquals(1, tracer.interpretResponseCalls.size)

            val expectedRequestId = tracer.requestWillBeSentCalls[0].requestId

            with(tracer.requestWillBeSentCalls[0]) {
                assertEquals(HttpMethod.Get, requestData.method)
                assertEquals("http://www.google.com/", requestData.url.toString())
                assertEquals(expectedRequestId, requestId)
            }

            with(tracer.responseHeadersReceivedCalls[0]) {
                assertEquals(HttpStatusCode.OK, responseData.statusCode)
                assertEquals(expectedRequestId, requestId)
            }

            with(tracer.interpretResponseCalls[0]) {
                assertEquals(expectedRequestId, requestId)
            }
        }
    }

    @Test
    fun testOutgoingChannelTracerOffer() = runBlocking {
        val channel = Channel<Frame>(Channel.Factory.UNLIMITED)
        try {
            val tracer = TestTracer()
            val channelTracer = OutgoingChannelTracer("42", tracer, channel)
            val deferred = async { channel.receive() }
            while (!channelTracer.offer(Frame.Text("Text"))) {
            }
            deferred.await()

            assertEquals(1, tracer.webSocketFrameSentCalls.size)
            with(tracer.webSocketFrameSentCalls[0]) {
                assertEquals("42", requestId)
                assertTrue(frame is Frame.Text)
                assertEquals("Text", (frame as Frame.Text).readText())
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun testOutgoingChannelTracerSend() = runBlocking {
        val channel = Channel<Frame>(Channel.Factory.UNLIMITED)
        try {
            val tracer = TestTracer()
            val channelTracer = OutgoingChannelTracer("42", tracer, channel)
            val deferred = async { channel.receive() }
            channelTracer.send(Frame.Text("Text"))
            deferred.await()

            assertEquals(1, tracer.webSocketFrameSentCalls.size)
            with(tracer.webSocketFrameSentCalls[0]) {
                assertEquals("42", requestId)
                assertTrue(frame is Frame.Text)
                assertEquals("Text", (frame as Frame.Text).readText())
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun testIncomingChannelTracerIterator() = runBlocking {
        val channel = Channel<Frame>(Channel.Factory.UNLIMITED)
        try {
            val tracer = TestTracer()
            val channelTracer = IncomingChannelTracer("42", tracer, channel)
            val deferred = async {
                channel.send(Frame.Text("Text1"))
                channel.send(Frame.Text("Text2"))
                channel.send(Frame.Text("Text3"))
                channel.close()
            }

            for (frame in channelTracer) {
            }

            deferred.await()

            assertEquals(3, tracer.webSocketFrameReceivedCalls.size)
            for (i in 1..3) {
                with(tracer.webSocketFrameReceivedCalls[i - 1]) {
                    assertEquals("42", requestId)
                    assertTrue(frame is Frame.Text)
                    assertEquals("Text$i", (frame as Frame.Text).readText())
                }
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun testIncomingChannelTracerPoll() = runBlocking {
        val channel = Channel<Frame>(Channel.Factory.UNLIMITED)
        try {
            val tracer = TestTracer()
            val channelTracer = IncomingChannelTracer("42", tracer, channel)
            val deferred = async { channel.send(Frame.Text("Text")) }

            while (channelTracer.poll() == null) {
                yield()
            }

            deferred.await()

            assertEquals(1, tracer.webSocketFrameReceivedCalls.size)
            with(tracer.webSocketFrameReceivedCalls[0]) {
                assertEquals("42", requestId)
                assertTrue(frame is Frame.Text)
                assertEquals("Text", (frame as Frame.Text).readText())
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun testIncomingChannelTracerReceive() = runBlocking {
        val channel = Channel<Frame>(Channel.Factory.UNLIMITED)
        try {
            val tracer = TestTracer()
            val channelTracer = IncomingChannelTracer("42", tracer, channel)
            val deferred = async { channel.send(Frame.Text("Text")) }

            channelTracer.receive()

            deferred.await()

            assertEquals(1, tracer.webSocketFrameReceivedCalls.size)
            with(tracer.webSocketFrameReceivedCalls[0]) {
                assertEquals("42", requestId)
                assertTrue(frame is Frame.Text)
                assertEquals("Text", (frame as Frame.Text).readText())
            }
        } finally {
            channel.close()
        }
    }

    @Test
    @OptIn(InternalCoroutinesApi::class)
    fun testIncomingChannelTracerReceiveOrClosed() = runBlocking {
        val channel = Channel<Frame>(Channel.Factory.UNLIMITED)
        try {
            val tracer = TestTracer()
            val channelTracer = IncomingChannelTracer("42", tracer, channel)
            val deferred = async { channel.send(Frame.Text("Text")) }

            channelTracer.receiveOrClosed()

            deferred.await()

            assertEquals(1, tracer.webSocketFrameReceivedCalls.size)
            with(tracer.webSocketFrameReceivedCalls[0]) {
                assertEquals("42", requestId)
                assertTrue(frame is Frame.Text)
                assertEquals("Text", (frame as Frame.Text).readText())
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun testIncomingChannelTracerReceiveOrNull() = runBlocking {
        val channel = Channel<Frame>(Channel.Factory.UNLIMITED)
        try {
            val tracer = TestTracer()
            val channelTracer = IncomingChannelTracer("42", tracer, channel)
            val deferred = async { channel.send(Frame.Text("Text")) }

            channelTracer.receiveOrNull()

            deferred.await()

            assertEquals(1, tracer.webSocketFrameReceivedCalls.size)
            with(tracer.webSocketFrameReceivedCalls[0]) {
                assertEquals("42", requestId)
                assertTrue(frame is Frame.Text)
                assertEquals("Text", (frame as Frame.Text).readText())
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun testTracingWrapperExecute() = runBlocking {
        val requestData = HttpRequestData(
            Url("http://www.google.com"),
            HttpMethod.Get,
            Headers.Empty,
            object : OutgoingContent.ByteArrayContent() {
                override fun bytes(): ByteArray = ByteArray(42)
            },
            Job(),
            Attributes()
        )
        val responseData =
            HttpResponseData(HttpStatusCode.OK, GMTDate(), Headers.Empty, HttpProtocolVersion.HTTP_1_1, "body", Job())

        val tracer = TestTracer()
        val tracingWrapperFactory = TracingWrapper(TestHttpClientEngineFactory(responseData), tracer)
        val tracingWrapper = tracingWrapperFactory.create()

        tracingWrapper.execute(requestData)

        assertEquals(1, tracer.requestWillBeSentCalls.size)
        assertEquals(1, tracer.responseHeadersReceivedCalls.size)
        assertEquals(1, tracer.interpretResponseCalls.size)

        with(tracer.requestWillBeSentCalls[0]) {
            assertEquals("0", requestId)
            assertEquals(requestData, this.requestData)
        }

        with(tracer.responseHeadersReceivedCalls[0]) {
            assertEquals("0", requestId)
            assertEquals(requestData, this.requestData)
            assertEquals(responseData, this.responseData)
        }

        with(tracer.interpretResponseCalls[0]) {
            assertEquals("0", requestId)
            assertEquals("body", body)
        }
    }
}

class TestHttpClientEngineFactory(private val responseData: HttpResponseData) :
    HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return TestHttpClientEngine(responseData)
    }

}

class TestHttpClientEngine(private val responseData: HttpResponseData) : HttpClientEngine {
    override val config: HttpClientEngineConfig = HttpClientEngineConfig()
    override val coroutineContext: CoroutineContext = Job()
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default

    override fun close() {}

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        return responseData
    }
}
