/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

internal class ServerConnectionBasedSocket(
    connection: Connection,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : TSocketImpl<SocketChannel>(connection, connection.channel, selector, pool = null, socketOptions = socketOptions),
    Socket {
    init {
        require(!channel.isBlocking) { "Channel need to be configured as non-blocking." }
    }

    override val localAddress: SocketAddress
        get() {
            val localAddress = if (java7NetworkApisAvailable) {
                channel.localAddress
            } else {
                channel.socket().localSocketAddress
            }
            return localAddress?.toSocketAddress()
                ?: throw IllegalStateException("Channel is not yet bound")
        }

    override val remoteAddress: SocketAddress
        get() {
            val remoteAddress = if (java7NetworkApisAvailable) {
                channel.remoteAddress
            } else {
                channel.socket().remoteSocketAddress
            }
            return remoteAddress?.toSocketAddress()
                ?: throw IllegalStateException("Channel is not yet connected")
        }
}

internal abstract class TSocketImpl<out S>(
    val connection: Connection,
    override val channel: S,
    val selector: SelectorManager,
    val pool: ObjectPool<ByteBuffer>?,
    private val socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : ReadWriteSocket, SelectableBase(channel), CoroutineScope
    where S : java.nio.channels.ByteChannel, S : SelectableChannel {

    private val closeFlag = AtomicBoolean()

    @Suppress("DEPRECATION")
    private val readerJob = AtomicReference<ReaderJob?>()

    @Suppress("DEPRECATION")
    private val writerJob = AtomicReference<WriterJob?>()

    override val socketContext: CompletableJob = Job()

    override val coroutineContext: CoroutineContext
        get() = socketContext

    // NOTE: it is important here to use different versions of attachForReadingImpl
    // because it is not always valid to use channel's internal buffer for NIO read/write:
    //  at least UDP datagram reading MUST use bigger byte buffer otherwise datagram could be truncated
    //  that will cause broken data
    // however it is not the case for attachForWriting this is why we use direct writing in any case

    @Suppress("DEPRECATION")
    final override fun attachForReading(channel: io.ktor.utils.io.ByteChannel): WriterJob {
        return attachFor("reading", channel, writerJob) {
            if (pool != null) {
                attachForReadingImplE(channel, connection, pool, socketOptions)
            } else {
                attachForReadingDirectImplE(channel, connection, socketOptions)
            }
        }
    }

    @Suppress("DEPRECATION")
    final override fun attachForWriting(channel: io.ktor.utils.io.ByteChannel): ReaderJob {
        return attachFor("writing", channel, readerJob) {
            attachForWritingDirectImplE(channel, connection, socketOptions)
        }
    }

    override fun dispose() {
        close()
    }

    override fun close() {
        if (!closeFlag.compareAndSet(false, true)) return

        readerJob.get()?.channel?.close()
        writerJob.get()?.cancel()
        checkChannels()
    }

    @Suppress("DEPRECATION")
    private fun <J : Job> attachFor(
        name: String,
        channel: io.ktor.utils.io.ByteChannel,
        ref: AtomicReference<J?>,
        producer: () -> J
    ): J {
        if (closeFlag.get()) {
            val e = ClosedChannelException()
            channel.close(e)
            throw e
        }

        val j = producer()

        if (!ref.compareAndSet(null, j)) {
            val e = IllegalStateException("$name channel has already been set")
            j.cancel()
            throw e
        }
        if (closeFlag.get()) {
            val e = ClosedChannelException()
            j.cancel()
            channel.close(e)
            throw e
        }

        channel.attachJob(j)

        j.invokeOnCompletion {
            checkChannels()
        }

        return j
    }

    private fun actualClose(): Throwable? {
        return try {
            channel.close()
            super.close()
            null
        } catch (cause: Throwable) {
            cause
        } finally {
            selector.notifyClosed(this)
        }
    }

    private fun checkChannels() {
        if (closeFlag.get() && readerJob.completedOrNotStarted && writerJob.completedOrNotStarted) {
            val e1 = readerJob.exception
            val e2 = writerJob.exception
            val e3 = actualClose()

            val combined = combine(combine(e1, e2), e3)

            if (combined == null) socketContext.complete() else socketContext.completeExceptionally(combined)
        }
    }

    private fun combine(e1: Throwable?, e2: Throwable?): Throwable? = when {
        e1 == null -> e2
        e2 == null -> e1
        e1 === e2 -> e1
        else -> {
            e1.addSuppressed(e2)
            e1
        }
    }

    private val AtomicReference<out Job?>.completedOrNotStarted: Boolean
        get() = get().let { it == null || it.isCompleted }

    @OptIn(InternalCoroutinesApi::class)
    private val AtomicReference<out Job?>.exception: Throwable?
        get() = get()?.takeIf { it.isCancelled }
            ?.getCancellationException()?.cause // TODO it should be completable deferred or provide its own exception
}

@Suppress("DEPRECATION")
internal fun CoroutineScope.attachForReadingImplE(
    channel: ByteChannel,
    connection: Connection,
    pool: ObjectPool<ByteBuffer>,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): WriterJob {
    val buffer = pool.borrow()
    return writer(Dispatchers.Unconfined + CoroutineName("cio-from-nio-reader"), channel) {
        try {
            val timeout = if (socketOptions?.socketTimeout != null) {
                createTimeout("reading", socketOptions.socketTimeout) {
                    channel.close(SocketTimeoutException())
                }
            } else {
                null
            }

            while (true) {
                var rc = 0

                timeout.withTimeout {
                    do {
                        rc = connection.readToE(buffer)
                        if (rc == 0) {
                            channel.flush()
                        }
                    } while (rc == 0)
                }

                if (rc == -1) {
                    channel.close()
                    break
                } else {
                    buffer.flip()
                    channel.writeFully(buffer)
                    buffer.clear()
                }
            }
            timeout?.finish()
        } finally {
            pool.recycle(buffer)
            try {
                if (java7NetworkApisAvailable) {
                    connection.channel.shutdownInput()
                } else {
                    connection.channel.socket().shutdownInput()
                }
            } catch (ignore: ClosedChannelException) {
            }
        }
    }
}

@Suppress("DEPRECATION")
internal fun CoroutineScope.attachForReadingDirectImplE(
    channel: ByteChannel,
    connection: Connection,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): WriterJob = writer(Dispatchers.Unconfined + CoroutineName("cio-from-nio-reader"), channel) {
    try {
        val timeout = if (socketOptions?.socketTimeout != null) {
            createTimeout("reading-direct", socketOptions.socketTimeout) {
                channel.close(SocketTimeoutException())
            }
        } else {
            null
        }

        while (!channel.isClosedForWrite) {
            timeout.withTimeout {
                val rc = channel.readFromE(connection)

                if (rc == -1) {
                    channel.close()
                    return@withTimeout
                }

                if (rc > 0) return@withTimeout

                channel.flush()

                while (true) {
                    if (channel.readFromE(connection) != 0) break
                }
            }
        }

        timeout?.finish()
        channel.closedCause?.let { throw it }
        channel.close()
    } finally {
        try {
            if (java7NetworkApisAvailable) {
                connection.channel.shutdownInput()
            } else {
                connection.channel.socket().shutdownInput()
            }
        } catch (ignore: ClosedChannelException) {
        }
    }
}

private suspend fun ByteWriteChannel.readFromE(connection: Connection): Int {
    var count = 0
    connection.performRead { channel ->
        write { buffer ->
            count = channel.read(buffer)
        }
    }

    return count
}

private suspend fun Connection.readToE(receivedRequest: ByteBuffer): Int {
    return performRead {
        it.read(receivedRequest)
    }
}



//_--------------------------------------------------------
@Suppress("DEPRECATION")
internal fun CoroutineScope.attachForWritingImplE(
    channel: ByteChannel,
    nioChannel: WritableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    pool: ObjectPool<ByteBuffer>,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): ReaderJob {
    val buffer = pool.borrow()

    return reader(Dispatchers.Unconfined + CoroutineName("cio-to-nio-writer"), channel) {
        try {
            val timeout = if (socketOptions?.socketTimeout != null) {
                createTimeout("writing", socketOptions.socketTimeout) {
                    channel.close(SocketTimeoutException())
                }
            } else {
                null
            }

            while (true) {
                buffer.clear()
                if (channel.readAvailable(buffer) == -1) {
                    break
                }
                buffer.flip()

                while (buffer.hasRemaining()) {
                    var rc: Int

                    timeout.withTimeout {
                        do {
                            rc = nioChannel.write(buffer)
                            if (rc == 0) {
                                selectable.interestOp(SelectInterest.WRITE, true)
                                selector.select(selectable, SelectInterest.WRITE)
                            }
                        } while (buffer.hasRemaining() && rc == 0)
                    }

                    selectable.interestOp(SelectInterest.WRITE, false)
                }
            }
            timeout?.finish()
        } finally {
            pool.recycle(buffer)
            if (nioChannel is SocketChannel) {
                try {
                    if (java7NetworkApisAvailable) {
                        nioChannel.shutdownOutput()
                    } else {
                        nioChannel.socket().shutdownOutput()
                    }
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
internal fun CoroutineScope.attachForWritingDirectImplE(
    channel: ByteChannel,
    connection: Connection,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): ReaderJob = reader(Dispatchers.Unconfined + CoroutineName("cio-to-nio-writer"), channel) {
    try {
        @Suppress("DEPRECATION")
        channel.lookAheadSuspend {
            val timeout = if (socketOptions?.socketTimeout != null) {
                createTimeout("writing-direct", socketOptions.socketTimeout) {
                    channel.close(SocketTimeoutException())
                }
            } else {
                null
            }

            while (true) {
                val buffer = request(0, 1)
                if (buffer == null) {
                    if (!awaitAtLeast(1)) break
                    continue
                }

                while (buffer.hasRemaining()) {
                    var rc = 0

                    timeout.withTimeout {
                        do {
                            rc = connection.writeFromE(buffer)
                        } while (buffer.hasRemaining() && rc == 0)
                    }

                    consumed(rc)
                }
            }
            timeout?.finish()
        }
    } finally {
        try {
            if (java7NetworkApisAvailable) {
                connection.channel.shutdownOutput()
            } else {
                connection.channel.socket().shutdownOutput()
            }
        } catch (ignore: ClosedChannelException) {
        }
    }
}

private suspend fun Connection.writeFromE(receivedRequest: ByteBuffer): Int {
    return performWrite {
        it.write(receivedRequest)
    }
}
