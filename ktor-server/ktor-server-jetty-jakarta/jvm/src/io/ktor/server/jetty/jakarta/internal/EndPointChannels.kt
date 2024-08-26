/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta.internal

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import io.ktor.utils.io.pool.ByteBufferPool
import kotlinx.coroutines.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.util.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

private const val JETTY_WEBSOCKET_POOL_SIZE = 2000

private val EndpointReaderCoroutineName = CoroutineName("jetty-upgrade-endpoint-reader")

private val EndpointWriterCoroutineName = CoroutineName("jetty-upgrade-endpoint-writer")

private val JettyWebSocketPool = ByteBufferPool(JETTY_WEBSOCKET_POOL_SIZE, 4096)

internal class EndPointReader(
    endpoint: EndPoint,
    override val coroutineContext: CoroutineContext,
    private val channel: ByteWriteChannel
) : AbstractConnection(endpoint, coroutineContext.executor()), Connection.UpgradeTo, CoroutineScope {
    private val currentHandler = AtomicReference<Continuation<Unit>>()
    private val buffer = JettyWebSocketPool.borrow()

    init {
        runReader()
    }

    private fun runReader(): Job {
        return launch(EndpointReaderCoroutineName + Dispatchers.Unconfined) {
            try {
                while (true) {
                    buffer.clear()
                    suspendCancellableCoroutine<Unit> { continuation ->
                        currentHandler.compareAndSet(null, continuation)
                        fillInterested()
                    }

                    channel.writeFully(buffer)
                }
            } catch (cause: ClosedChannelException) {
                channel.flushAndClose()
            } catch (cause: Throwable) {
                channel.close(cause)
            } finally {
                channel.flushAndClose()
                JettyWebSocketPool.recycle(buffer)
            }
        }
    }

    override fun onFillable() {
        val handler = currentHandler.getAndSet(null) ?: return
        buffer.flip()
        val count = try {
            endPoint.fill(buffer)
        } catch (cause: Throwable) {
            handler.resumeWithException(ClosedChannelException())
        }

        if (count == -1) {
            handler.resumeWithException(ClosedChannelException())
        } else {
            handler.resume(Unit)
        }
    }

    override fun onFillInterestedFailed(cause: Throwable) {
        super.onFillInterestedFailed(cause)
        if (cause is ClosedChannelException) {
            currentHandler.getAndSet(null)?.resumeWithException(cause)
        } else {
            currentHandler.getAndSet(null)?.resumeWithException(ChannelReadException(exception = cause))
        }
    }

    override fun failedCallback(callback: Callback, cause: Throwable) {
        super.failedCallback(callback, cause)

        val handler = currentHandler.getAndSet(null) ?: return
        handler.resumeWithException(ChannelReadException(exception = cause))
    }

    override fun onUpgradeTo(prefilled: ByteBuffer?) {
        if (prefilled != null && prefilled.hasRemaining()) {
            // println("Got prefilled ${prefilled.remaining()} bytes")
            // in theory client could try to start communication with no server upgrade acknowledge
            // it is generally not the case because clients negotiates first then communicate
        }
    }
}

internal fun CoroutineScope.endPointWriter(
    endPoint: EndPoint,
    pool: ObjectPool<ByteBuffer> = JettyWebSocketPool
): ReaderJob = reader(EndpointWriterCoroutineName + Dispatchers.Unconfined, autoFlush = true) {
    pool.useInstance { buffer: ByteBuffer ->
        val source = channel

        while (!source.isClosedForRead) {
            buffer.clear()
            if (source.readAvailable(buffer) == -1) break

            buffer.flip()
            endPoint.write(buffer)
        }
        endPoint.flush()

        source.closedCause?.let { throw it }
    }
}

private suspend fun EndPoint.write(buffer: ByteBuffer) = suspendCancellableCoroutine<Unit> { continuation ->
    write(
        object : Callback {
            override fun succeeded() {
                continuation.resume(Unit)
            }

            override fun failed(cause: Throwable) {
                continuation.resumeWithException(ChannelWriteException(exception = cause))
            }
        },
        buffer
    )
}

private fun CoroutineContext.executor(): Executor = object : Executor, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = this@executor

    override fun execute(command: Runnable?) {
        launch { command?.run() }
    }
}
