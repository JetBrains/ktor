/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.locks.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.*
import kotlin.jvm.*

internal expect val DEVELOPMENT_MODE: Boolean
internal const val CHANNEL_MAX_SIZE: Int = 1024 * 1024

/**
 * Sequential (non-concurrent) byte channel implementation
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.ByteChannel)
 */
public class ByteChannel(public val autoFlush: Boolean = false) : ByteReadChannel, BufferedByteWriteChannel {
    private val flushBuffer: Buffer = Buffer()

    @Volatile
    private var flushBufferSize = 0

    @OptIn(InternalAPI::class)
    private val flushBufferMutex = SynchronizedObject()

    // Awaiting slot, handles suspension when waiting for I/O
    private val suspensionSlot: AtomicRef<Slot> = atomic(Slot.Empty)

    private val _readBuffer = Buffer()
    private val _writeBuffer = Buffer()
    private val _closedCause = atomic<CloseToken?>(null)

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
        if (_readBuffer.size >= min) return true

        sleepWhile(Slot::Read) {
            flushBufferSize + _readBuffer.size < min && _closedCause.value == null
        }

        if (_readBuffer.size < CHANNEL_MAX_SIZE) moveFlushToReadBuffer()
        return _readBuffer.size >= min
    }

    @OptIn(InternalAPI::class)
    private fun moveFlushToReadBuffer() {
        synchronized(flushBufferMutex) {
            flushBuffer.transferTo(_readBuffer)
            flushBufferSize = 0
        }

        resumeSlot<Slot.Write>()
    }

    @OptIn(InternalAPI::class)
    override suspend fun flush() {
        rethrowCloseCauseIfNeeded()

        flushWriteBuffer()
        if (flushBufferSize < CHANNEL_MAX_SIZE) return

        sleepWhile(Slot::Write) {
            flushBufferSize >= CHANNEL_MAX_SIZE && _closedCause.value == null
        }
    }

    @InternalAPI
    public override fun flushWriteBuffer() {
        if (_writeBuffer.exhausted()) return

        synchronized(flushBufferMutex) {
            val count = _writeBuffer.size.toInt()
            flushBuffer.transferFrom(_writeBuffer)
            flushBufferSize += count
        }

        resumeSlot<Slot.Read>()
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

    private suspend inline fun <reified TaskType : Slot.Task> sleepWhile(
        crossinline createTask: (Continuation<Unit>) -> TaskType,
        crossinline shouldSleep: () -> Boolean
    ) {
        while (shouldSleep()) {
            suspendCancellableCoroutine { continuation ->
                trySuspend<TaskType>(createTask(continuation), shouldSleep)
            }
        }
    }

    /**
     * Clears and resumes expected slot.
     *
     * For example, after flushing the write buffer, we can resume reads.
     */
    private inline fun <reified Expected : Slot.Task> resumeSlot() {
        val current = suspensionSlot.value
        if (current is Expected && suspensionSlot.compareAndSet(current, Slot.Empty)) {
            current.resume()
        }
    }

    /**
     * Cancel waiter.
     */
    private fun closeSlot(cause: Throwable?) {
        val closeContinuation = if (cause != null) Slot.Closed(cause) else Slot.CLOSED
        val continuation = suspensionSlot.getAndSet(closeContinuation)
        if (continuation !is Slot.Task) return

        continuation.resume(cause)
    }

    private inline fun <reified TaskType : Slot.Task> trySuspend(
        slot: TaskType,
        crossinline shouldSleep: () -> Boolean,
    ) {
        // Replace the previous task
        val previous = suspensionSlot.value
        if (previous !is Slot.Closed) {
            if (!suspensionSlot.compareAndSet(previous, slot)) {
                slot.resume()
                return
            }
        }

        // Resume the previous task
        when (previous) {
            is TaskType ->
                previous.resume(ConcurrentIOException(slot.taskName(), previous.created))

            is Slot.Task ->
                previous.resume()

            is Slot.Closed -> {
                slot.resume(previous.cause)
                return
            }

            Slot.Empty -> {}
        }

        // Suspend if buffer unchanged
        if (!shouldSleep()) {
            resumeSlot<TaskType>()
        }
    }

    private sealed interface Slot {
        companion object {
            @JvmStatic
            val CLOSED = Closed(null)

            @JvmStatic
            val RESUME = Result.success(Unit)
        }

        data object Empty : Slot

        data class Closed(val cause: Throwable?) : Slot

        sealed interface Task : Slot {
            val created: Throwable?

            val continuation: Continuation<Unit>

            fun taskName(): String

            fun resume() =
                continuation.resumeWith(RESUME)

            fun resume(throwable: Throwable? = null) =
                continuation.resumeWith(throwable?.let { Result.failure(it) } ?: RESUME)
        }

        class Read(override val continuation: Continuation<Unit>) : Task {
            override var created: Throwable? = null

            init {
                if (DEVELOPMENT_MODE) {
                    created = Throwable("ReadTask 0x${continuation.hashCode().toString(16)}").also {
                        it.stackTraceToString()
                    }
                }
            }

            override fun taskName(): String = "read"
        }

        class Write(override val continuation: Continuation<Unit>) : Task {
            override var created: Throwable? = null

            init {
                if (DEVELOPMENT_MODE) {
                    created = Throwable("WriteTask 0x${continuation.hashCode().toString(16)}").also {
                        it.stackTraceToString()
                    }
                }
            }

            override fun taskName(): String = "write"
        }
    }
}

/**
 * Thrown when a coroutine awaiting I/O is replaced by another.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.ConcurrentIOException)
 */
public class ConcurrentIOException(
    taskName: String,
    cause: Throwable? = null
) : IllegalStateException("Concurrent $taskName attempts", cause)
