/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.apache

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.apache.http.*
import org.apache.http.nio.*
import org.apache.http.nio.ContentEncoder
import java.nio.*
import kotlin.coroutines.*
import kotlin.test.*

@Suppress("BlockingMethodInNonBlockingContext")
class RequestProducerTest {

    @OptIn(InternalAPI::class)
    @Test
    fun testHeadersMerge() = runBlocking {
        val request = ApacheRequestProducer(
            HttpRequestData(
                Url("http://127.0.0.1/"),
                HttpMethod.Post,
                Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Text.Plain)
                    append(HttpHeaders.ContentLength, "1")
                },
                TextContent("{}", ContentType.Application.Json),
                Job(),
                Attributes()
            ),
            ApacheEngineConfig(),
            EmptyCoroutineContext
        ).generateRequest() as HttpEntityEnclosingRequest

        assertEquals(ContentType.Application.Json.toString(), request.entity.contentType.value)
        assertEquals(2, request.entity.contentLength)
    }

    @Test
    fun testProducingByteArrayContent() = runBlocking {
        val bytes = "x".repeat(10000).toByteArray()
        val producer = producer(ByteArrayContent(bytes), coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        while (!encoder.isCompleted) {
            if (ioctrl.outputSuspended) continue
            producer.produceContent(encoder, ioctrl)
        }

        assertEquals("x".repeat(10000), result.await())
        producer.close()
    }

    @Test
    fun testProducingNoContent() = runBlocking {
        val producer = producer(object : OutgoingContent.NoContent() {}, coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        while (!encoder.isCompleted) {
            if (ioctrl.outputSuspended) continue
            producer.produceContent(encoder, ioctrl)
        }

        assertEquals("", result.await())
        producer.close()
    }

    @OptIn(DelicateCoroutinesApi::class, InternalAPI::class)
    @Test
    fun testProducingReadChannelContent() = runBlocking {
        val content = ByteChannel(true)
        val body = object : OutgoingContent.ReadChannelContent() {
            override fun readFrom(): ByteReadChannel = content
        }
        val producer = producer(body, coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        GlobalScope.launch {
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.close()
        }

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        while (!encoder.isCompleted) {
            if (ioctrl.outputSuspended) continue
            producer.produceContent(encoder, ioctrl)
        }

        assertEquals("xxxxx", result.await())
        producer.close()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testProducingWriteChannelContent() = runBlocking {
        val body = ChannelWriterContent(
            body = {
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
            },
            contentType = null
        )
        val producer = producer(body, coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        GlobalScope.launch {
            while (!encoder.isCompleted) {
                if (ioctrl.outputSuspended) continue
                producer.produceContent(encoder, ioctrl)
            }
        }

        assertEquals("xxxxx", result.await())
        producer.close()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testProducingWriteChannelContentOnScale() = runBlocking {
        repeat(5000) {
            val chunk = (0 until 10_000).map { it.toByte() }.toByteArray()
            val body = ChannelWriterContent(
                body = { writeFully(chunk) },
                contentType = null
            )
            val producer = producer(body, coroutineContext)

            val encoder = TestEncoder()
            val ioctrl = TestIOControl()

            val result = async {
                val result = ByteArray(10000)
                encoder.channel.readFully(result)
                result
            }

            GlobalScope.launch {
                while (!encoder.isCompleted) {
                    if (ioctrl.outputSuspended) continue
                    producer.produceContent(encoder, ioctrl)
                }
            }

            assertEquals(chunk.toList(), result.await().toList())
            producer.close()
        }
    }

    @OptIn(InternalAPI::class)
    private fun producer(body: OutgoingContent, context: CoroutineContext) = ApacheRequestProducer(
        requestData = HttpRequestData(
            URLBuilder("https://example.com").build(),
            HttpMethod.Get,
            Headers.Empty,
            body,
            Job(),
            Attributes()
        ),
        config = ApacheEngineConfig(),
        callContext = context
    )

    @Test
    fun expectToThrowIllegalStateExceptionIfHostCannotBeExtractedFromRequestURL() {
        val request = HttpRequestBuilder { takeFrom("file://") }.build()
        val cause = assertFailsWith<IllegalArgumentException> {
            ApacheRequestProducer(request, ApacheEngineConfig(), EmptyCoroutineContext)
        }
        assertEquals("Cannot extract host from URL file:///", cause.message)
    }
}

private class TestEncoder : ContentEncoder {
    val channel = ByteChannel()

    override fun write(src: ByteBuffer): Int = runBlocking {
        channel.writeAvailable(src)
        src.limit()
    }

    @OptIn(InternalAPI::class)
    override fun complete() {
        channel.close()
    }

    override fun isCompleted(): Boolean = channel.isClosedForWrite
}

private class TestIOControl : IOControl {

    @Volatile
    var inputSuspended = false
        private set

    @Volatile
    var outputSuspended = false
        private set

    override fun requestInput() {
        inputSuspended = false
    }

    override fun suspendInput() {
        inputSuspended = true
    }

    override fun requestOutput() {
        outputSuspended = false
    }

    override fun suspendOutput() {
        outputSuspended = true
    }

    override fun shutdown() {
    }
}
