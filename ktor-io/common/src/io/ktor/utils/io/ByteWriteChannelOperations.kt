/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.intrinsics.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import kotlin.coroutines.*
import kotlin.jvm.*

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeByte(value: Byte) {
    writeBuffer.writeByte(value)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeShort(value: Short) {
    writeBuffer.writeShort(value)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeInt(value: Int) {
    writeBuffer.writeInt(value)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeLong(value: Long) {
    writeBuffer.writeLong(value)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeByteArray(array: ByteArray) {
    writeBuffer.write(array)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeSource(source: Source) {
    writeBuffer.transferFrom(source)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeString(value: String) {
    writeBuffer.writeText(value)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeFully(value: ByteArray, startIndex: Int = 0, endIndex: Int = value.size) {
    writeBuffer.write(value, startIndex, endIndex)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeBuffer(value: Source) {
    writeBuffer.transferFrom(value)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writeStringUtf8(value: String) {
    writeBuffer.writeText(value)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writePacket(copy: Buffer) {
    writeBuffer.transferFrom(copy)
    flushIfNeeded()
}

@OptIn(InternalAPI::class)
public suspend fun ByteWriteChannel.writePacket(copy: Source) {
    writeBuffer.transferFrom(copy)
    flushIfNeeded()
}

public fun ByteWriteChannel.close(cause: Throwable?) {
    if (cause == null) {
        ::flushAndClose.fireAndForget()
    } else {
        cancel(cause)
    }
}

public class WriterScope(
    public val channel: ByteWriteChannel,
    override val coroutineContext: CoroutineContext
) : CoroutineScope

public interface ChannelJob {
    public val job: Job
}

public suspend fun ChannelJob.join() {
    job.join()
}

public val ChannelJob.isCompleted: Boolean get() = job.isCompleted

public val ChannelJob.isCancelled: Boolean get() = job.isCancelled

@OptIn(InternalCoroutinesApi::class)
public fun ChannelJob.getCancellationException(): CancellationException = job.getCancellationException()

public fun ChannelJob.invokeOnCompletion(block: () -> Unit) {
    job.invokeOnCompletion { block() }
}

public fun ChannelJob.cancel(): Unit = job.cancel()

public class WriterJob internal constructor(
    public val channel: ByteReadChannel,
    public override val job: Job
) : ChannelJob

@Suppress("UNUSED_PARAMETER")
public fun CoroutineScope.writer(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    autoFlush: Boolean = false,
    block: suspend WriterScope.() -> Unit
): WriterJob = writer(coroutineContext, ByteChannel(), block)

@OptIn(InternalCoroutinesApi::class)
public fun CoroutineScope.writer(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    channel: ByteChannel,
    block: suspend WriterScope.() -> Unit
): WriterJob {
    val job = launch(coroutineContext) {
        val nested = Job(this.coroutineContext.job)
        try {
            block(WriterScope(channel, this.coroutineContext + nested))
            nested.complete()

            if (this.coroutineContext.job.isCancelled) {
                channel.cancel(this.coroutineContext.job.getCancellationException())
            }
        } catch (cause: Throwable) {
            nested.cancel("Exception thrown while writing to channel", cause)
            channel.cancel(cause)
        } finally {
            nested.join()
            runCatching { channel.flushAndClose() }
        }
    }.apply {
        invokeOnCompletion {
            if (it != null && !channel.isClosedForWrite) {
                channel.cancel(it)
            }
        }
    }

    return WriterJob(channel, job)
}

/**
 * Await for [desiredSpace] will be available for write and invoke [block] function providing [Memory] instance and
 * the corresponding range suitable for wiring in the memory. The block function should return number of bytes were
 * written, possibly 0.
 *
 * Similar to [ByteReadChannel.read], this function may invoke block function with lesser memory range when the
 * specified [desiredSpace] is bigger that the buffer's capacity
 * or when it is impossible to represent all [desiredSpace] bytes as a single memory range
 * due to internal implementation reasons.
 */
@OptIn(UnsafeIoApi::class, InternalAPI::class, InternalIoApi::class)
public suspend fun ByteWriteChannel.write(
    desiredSpace: Int = 1,
    block: (ByteArray, Int, Int) -> Int
): Int {
    val before = writeBuffer.size
    UnsafeBufferOperations.writeToTail(writeBuffer.buffer, desiredSpace, block)
    val after = writeBuffer.size
    val written = after - before
    flushIfNeeded()
    return written
}

public suspend fun ByteWriteChannel.awaitFreeSpace() {
    flush()
}

@OptIn(InternalCoroutinesApi::class)
internal fun <R> (suspend () -> R).fireAndForget() {
    this.startCoroutineCancellable(NO_CALLBACK)
}

private val NO_CALLBACK = object : Continuation<Any?> {
    override val context: CoroutineContext = EmptyCoroutineContext

    override fun resumeWith(result: Result<Any?>) = Unit
}
