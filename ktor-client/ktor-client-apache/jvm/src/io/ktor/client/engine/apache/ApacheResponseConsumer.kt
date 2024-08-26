/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import org.apache.http.*
import org.apache.http.nio.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import kotlin.coroutines.*

@OptIn(InternalCoroutinesApi::class)
internal class ApacheResponseConsumer(
    private val parentContext: CoroutineContext,
    private val requestData: HttpRequestData
) : HttpAsyncResponseConsumer<Unit>, CoroutineScope {
    private val interestController = InterestControllerHolder()

    private val consumerJob = Job(parentContext[Job])
    override val coroutineContext: CoroutineContext = parentContext + consumerJob

    private val waiting = atomic(false)

    private val channel = ByteChannel().also {
        it.attachJob(consumerJob)
    }

    private val responseDeferred = CompletableDeferred<HttpResponse>()

    val responseChannel: ByteReadChannel = channel

    init {
        coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { cause ->
            if (cause != null) {
                responseDeferred.completeExceptionally(cause)
                responseChannel.cancel(cause)
            }
        }
    }

    @OptIn(InternalAPI::class)
    override fun consumeContent(decoder: ContentDecoder, ioctrl: IOControl) {
        check(!waiting.value)

        var result: Int
        do {
            result = 0
            channel.writeAvailable {
                result = decoder.read(it)
            }
            channel.flushWriteBuffer()
        } while (result > 0)

        if (result < 0 || decoder.isCompleted) {
            close()
            return
        }

        if (result == 0) {
            interestController.suspendInput(ioctrl)
            launch(Dispatchers.Unconfined) {
                check(!waiting.getAndSet(true))
                try {
                    channel.awaitFreeSpace()
                } finally {
                    check(waiting.getAndSet(false))
                    interestController.resumeInputIfPossible()
                }
            }
        }
    }

    override fun failed(cause: Exception) {
        val mappedCause = mapCause(cause, requestData)
        consumerJob.completeExceptionally(mappedCause)
        responseDeferred.completeExceptionally(mappedCause)
        responseChannel.cancel(mappedCause)
    }

    override fun cancel(): Boolean {
        return true
    }

    override fun close() {
        channel.close()
        consumerJob.complete()
    }

    override fun getException(): Exception? = channel.closedCause as Exception?

    override fun getResult() = Unit

    override fun isDone(): Boolean = channel.isClosedForWrite

    override fun responseCompleted(context: HttpContext) = Unit

    override fun responseReceived(response: HttpResponse) {
        responseDeferred.complete(response)
    }

    suspend fun waitForResponse(): HttpResponse = responseDeferred.await()
}
