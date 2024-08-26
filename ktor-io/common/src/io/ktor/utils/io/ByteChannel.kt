/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.internal.*
import io.ktor.utils.io.locks.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.concurrent.*
import kotlin.coroutines.*

@InternalAPI
public val CHANNEL_MAX_SIZE: Int = 4096

/**
 * Sequential (non-concurrent) byte channel implementation
 */
public class ByteChannel(public val autoFlush: Boolean = false) : ByteReadChannel, BufferedByteWriteChannel {
    private val _closedCause = atomic<CloseToken?>(null)
    private val flushBuffer: Buffer = Buffer()

    @Volatile
    private var flushBufferSize = 0

    @OptIn(InternalAPI::class)
    private val mutex = SynchronizedObject()

    private val _readBuffer = Buffer()
    private val _writeBuffer = Buffer()

    @InternalAPI
    override val readBuffer: Source
        get() {
            closedCause?.let { throw it }
            if (_readBuffer.exhausted()) moveFlushToReadBuffer()
            return _readBuffer
        }

    @InternalAPI
    override val writeBuffer: Sink
        get() {
            closedCause?.let { throw it }
            if (isClosedForWrite) {
                throw IOException("Channel is closed for write")
            }
            return _writeBuffer
        }

    override val closedCause: Throwable?
        get() = _closedCause.value?.cause

    override val isClosedForWrite: Boolean
        get() = _closedCause.value != null

    override val isClosedForRead: Boolean
        get() = (closedCause != null) || (isClosedForWrite && flushBufferSize == 0 && _readBuffer.exhausted())

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        rethrowCloseCauseIfNeeded()
        if (flushBufferSize + _readBuffer.size >= min) return true

        sleepForRead(min)

        if (_readBuffer.size < CHANNEL_MAX_SIZE) moveFlushToReadBuffer()
        return _closedCause.value == null
    }

    @OptIn(InternalAPI::class)
    private fun moveFlushToReadBuffer() {
        synchronized(mutex) {
            flushBuffer.transferTo(_readBuffer)
            flushBufferSize = 0
        }

        resumeSlot()
    }

    @OptIn(InternalAPI::class)
    override suspend fun flush() {
        rethrowCloseCauseIfNeeded()

        flushWriteBuffer()
        if (flushBufferSize < CHANNEL_MAX_SIZE) return

        sleepForWrite()
    }

    @InternalAPI
    public override fun flushWriteBuffer() {
        if (_writeBuffer.exhausted()) return

        synchronized(mutex) {
            val count = _writeBuffer.size.toInt()
            flushBuffer.transferFrom(_writeBuffer)
            flushBufferSize += count
        }

        resumeSlot()
    }

    @OptIn(InternalAPI::class)
    override fun close() {
        flushWriteBuffer()

        // It's important to flush before we have closedCause set
        if (!_closedCause.compareAndSet(null, CLOSED)) return
        closeSlot(null)
    }

    override suspend fun flushAndClose() {
        runCatching {
            flush()
        }

        // It's important to flush before we have closedCause set
        if (!_closedCause.compareAndSet(null, CLOSED)) return
        closeSlot(null)
    }

    override fun cancel(cause: Throwable?) {
        if (_closedCause.value != null) return

        val closedToken = CloseToken(cause)
        _closedCause.compareAndSet(null, closedToken)
        val actualCause = closedToken.cause

        closeSlot(actualCause)
    }

    override fun toString(): String = "ByteChannel[${hashCode()}]"

    // Awaiting Slot
    private val suspensionSlot: AtomicRef<Continuation<Unit>?> = atomic(null)

    private suspend fun sleepForRead(min: Int) {
        while (flushBufferSize + _readBuffer.size < min && _closedCause.value == null) {
            suspendCancellableCoroutine {
//                readContinuation.init(it.intercepted())
                trySuspendSlot(it) { flushBufferSize + _readBuffer.size < min && _closedCause.value == null }
//                readContinuation.getOrThrow()
            }
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun sleepForWrite() {
        while (flushBufferSize >= CHANNEL_MAX_SIZE) {
            suspendCancellableCoroutine {
//                writeContinuation.init(it.intercepted())
                trySuspendSlot(it) { flushBufferSize >= CHANNEL_MAX_SIZE }
//                writeContinuation.getOrThrow()
            }
        }
    }

    /**
     * Resume waiter.
     */
    private fun resumeSlot() {
        val continuation = suspensionSlot.getAndUpdate {
            it as? ClosedSlot
        }

        continuation?.resume(Unit)
    }

    /**
     * Cancel waiter.
     */
    private fun closeSlot(cause: Throwable?) {
        val closeContinuation = if (cause != null) ClosedSlot(cause) else io.ktor.utils.io.internal.CLOSED
        val continuation = suspensionSlot.getAndSet(closeContinuation) ?: return
        if (continuation is ClosedSlot) return

        if (cause != null) {
            continuation.resumeWithException(cause)
        } else {
            continuation.resume(Unit)
        }
    }

    private inline fun trySuspendSlot(continuation: Continuation<Unit>, crossinline sleepCondition: () -> Boolean) {
        val published = suspensionSlot.compareAndSet(null, continuation)
        if (!published) {
            continuation.resume(Unit)
            return
        }

        if (!sleepCondition()) {
            suspensionSlot.getAndSet(null)?.resume(Unit)
        }
    }
}
