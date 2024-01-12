/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.nio.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.test.Test

class RequestProducerTest {

    @OptIn(InternalAPI::class)
    @Test
    fun testHeadersMerge() {
        val request = HttpRequestData(
            Url("http://127.0.0.1/"),
            HttpMethod.Post,
            Headers.build {
                append(HttpHeaders.ContentType, ContentType.Text.Plain)
                append(HttpHeaders.ContentLength, "1")
            },
            TextContent("{}", ContentType.Application.Json),
            Job(),
            Attributes()
        ).convertToHttpRequest(EmptyCoroutineContext)

        assertEquals(
            ContentType.Application.Json.toString(),
            request.headers().firstValue(HttpHeaders.ContentType).get()
        )
        assertEquals(2, request.bodyPublisher().get().contentLength())
    }

    @Test
    fun testByteReadChannelWriter() {
        val publisher = JavaHttpRequestBodyPublisher(EmptyCoroutineContext) {
            ByteReadChannel("{}")
        }
        val subscriber = ByteArraySubscriber()
        publisher.subscribe(subscriber)

        Thread.sleep(100)

        val result = subscriber.getResult()
        assertEquals("{}", String(result))
    }

    private class ByteArraySubscriber : Flow.Subscriber<ByteBuffer> {

        private val buffers = mutableListOf<ByteBuffer>()
        private var throwable: Throwable? = null

        override fun onSubscribe(subscription: Flow.Subscription) {
            subscription.request(1)
        }

        override fun onNext(item: ByteBuffer) {
            buffers += item
        }

        override fun onError(throwable: Throwable) {
            this.throwable = throwable
        }

        override fun onComplete() {
        }

        fun getResult(): ByteArray {
            throwable?.let { throw it }

            val size = buffers.sumOf { it.remaining() }
            var offset = 0
            val result = ByteArray(size)

            buffers.forEach { buffer ->
                val remaining = buffer.remaining()
                buffer.get(result, offset, remaining)
                offset += remaining
            }

            return result
        }
    }
}
