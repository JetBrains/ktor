/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

internal abstract class NIOSocketImpl<out S>(
    override val channel: S,
    val selector: SelectorManager,
    val pool: ObjectPool<ByteBuffer>?,
    private val socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : ReadWriteSocket, SelectableBase(channel), CoroutineScope
    where S : java.nio.channels.ByteChannel, S : SelectableChannel {

    private val closeFlag = AtomicBoolean()

    private val readerJob = AtomicReference<ReaderJob?>()

    private val writerJob = AtomicReference<WriterJob?>()

    override val socketContext: CompletableJob = Job()

    override val coroutineContext: CoroutineContext
        get() = socketContext

    // NOTE: it is important here to use different versions of attachForReadingImpl
    // because it is not always valid to use channel's internal buffer for NIO read/write:
    //  at least UDP datagram reading MUST use bigger byte buffer otherwise datagram could be truncated
    //  that will cause broken data
    // however it is not the case for attachForWriting this is why we use direct writing in any case

    final override fun attachForReading(channel: ByteChannel): WriterJob {
        return attachFor("reading", channel, writerJob) {
            if (pool != null) {
                attachForReadingImpl(channel, this.channel, this, selector, pool, socketOptions)
            } else {
                attachForReadingDirectImpl(channel, this.channel, this, selector, socketOptions)
            }
        }
    }

    final override fun attachForWriting(channel: ByteChannel): ReaderJob {
        return attachFor("writing", channel, readerJob) {
            attachForWritingDirectImpl(channel, this.channel, this, selector, socketOptions)
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

    private fun <J : ChannelJob> attachFor(
        name: String,
        channel: ByteChannel,
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

    private val AtomicReference<out ChannelJob?>.completedOrNotStarted: Boolean
        get() = get().let { it == null || it.isCompleted }

    @OptIn(InternalCoroutinesApi::class)
    private val AtomicReference<out ChannelJob?>.exception: Throwable?
        get() = get()?.takeIf { it.isCancelled }
            ?.getCancellationException()?.cause // TODO it should be completable deferred or provide its own exception
}
