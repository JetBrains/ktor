/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.net.http.*
import java.nio.*
import java.util.concurrent.*
import kotlin.coroutines.*

internal class JavaHttpRequestBodyPublisher(
    private val coroutineContext: CoroutineContext,
    private val contentLength: Long = -1,
    private val getChannel: () -> ByteReadChannel
) : HttpRequest.BodyPublisher {

    override fun contentLength(): Long {
        return contentLength
    }

    override fun subscribe(subscriber: Flow.Subscriber<in ByteBuffer>) {
        try {
            // We need to synchronize here because the subscriber could call
            // request() from within onSubscribe which would potentially
            // trigger onNext before onSubscribe is finished.
            val subscription = ReadableByteChannelSubscription(
                coroutineContext,
                getChannel(),
                subscriber
            )
            synchronized(subscription) { subscriber.onSubscribe(subscription) }
        } catch (cause: Throwable) {
            // subscribe() must return normally, so we need to signal the
            // failure to open via onError() once onSubscribe() is signaled.
            subscriber.onSubscribe(NullSubscription())
            subscriber.onError(cause)
        }
    }

    private class ReadableByteChannelSubscription(
        override val coroutineContext: CoroutineContext,
        private val inputChannel: ByteReadChannel,
        private val subscriber: Flow.Subscriber<in ByteBuffer>
    ) : Flow.Subscription, CoroutineScope {

        private val outstandingDemand = atomic(0L)
        private val writeInProgress = atomic(false)
        private val done = atomic(false)

        override fun request(n: Long) {
            if (done.value) return

            if (n < 1) {
                val cause = IllegalArgumentException(
                    "$subscriber violated the Reactive Streams rule 3.9 by requesting " +
                        "a non-positive number of elements."
                )
                signalOnError(cause)
                return
            }

            try {
                // As governed by rule 3.17, when demand overflows `Long.MAX_VALUE` we treat the signalled demand as
                // "effectively unbounded"
                outstandingDemand.getAndUpdate { initialDemand: Long ->
                    if (Long.MAX_VALUE - initialDemand < n) {
                        Long.MAX_VALUE
                    } else {
                        initialDemand + n
                    }
                }

                if (writeInProgress.compareAndSet(expect = false, update = true)) {
                    readData()
                }
            } catch (cause: Throwable) {
                signalOnError(cause)
            }
        }

        override fun cancel() {
            if (done.compareAndSet(expect = false, update = true)) {
                closeChannel()
            }
        }

        private fun checkHaveMorePermits(): Boolean {
            return writeInProgress.updateAndGet { outstandingDemand.decrementAndGet() > 0 }
        }

        private fun readData() {
            // It's possible to have another request for data come in after we've closed the channel.
            if (inputChannel.isClosedForRead) {
                tryToSignalOnErrorFromChannel()
                signalOnComplete()
                return
            }

            launch {
                do {
                    val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
                    val result = try {
                        inputChannel.readAvailable(buffer)
                    } catch (cause: Throwable) {
                        signalOnError(cause)
                        closeChannel()
                        return@launch
                    }

                    if (result > 0) {
                        buffer.flip()
                        signalOnNext(buffer)
                    }
                    // If we have more permits, queue up another read.
                } while (checkHaveMorePermits())

                if (inputChannel.isClosedForRead) {
                    // Reached the end of the channel, notify the subscriber and cleanup
                    signalOnComplete()
                    closeChannel()
                }
            }
        }

        private fun closeChannel() {
            try {
                inputChannel.cancel()
            } catch (cause: Throwable) {
                signalOnError(cause)
            }
        }

        private fun signalOnNext(buffer: ByteBuffer) {
            if (!done.value) {
                subscriber.onNext(buffer)
            }
        }

        private fun signalOnComplete() {
            if (done.compareAndSet(expect = false, update = true)) {
                subscriber.onComplete()
            }
        }

        private fun signalOnError(cause: Throwable) {
            if (done.compareAndSet(expect = false, update = true)) {
                subscriber.onError(cause)
            }
        }

        private fun tryToSignalOnErrorFromChannel() {
            inputChannel.closedCause?.let { cause ->
                signalOnError(cause)
            }
        }
    }

    private class NullSubscription : Flow.Subscription {
        override fun request(n: Long) {}
        override fun cancel() {}
    }
}
