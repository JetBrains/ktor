/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.cio

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.nio.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.channels.*
import kotlin.coroutines.*

/**
 * Launch a coroutine to open a read channel for a file and fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code and freeze the whole application when runs on a pool that is not intended for blocking operations.
 * This is why [coroutineContext] should have [Dispatchers.IO] or
 * a coroutine dispatcher that is properly configured for blocking IO.
 */
public fun File.readChannel(
    start: Long = 0,
    endInclusive: Long = -1,
    coroutineContext: CoroutineContext = Dispatchers.IO
): ByteReadChannel {
    val fileLength = length()
    val randomAccessFile by lazy { RandomAccessFile(this@readChannel, "r") }
    val writer = CoroutineScope(coroutineContext).writer(
        CoroutineName("file-reader") + coroutineContext,
        autoFlush = false
    ) {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= fileLength - 1) {
            "endInclusive points to the position out of the file: file size = $fileLength, endInclusive = $endInclusive"
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        randomAccessFile.use { file ->
            val fileChannel: FileChannel = file.channel
            fileChannel.writeToScope(this, start, endInclusive)
        }
    }

    writer.invokeOnCompletion {
        randomAccessFile.close()
    }

    return writer.channel
}

@Suppress("BlockingMethodInNonBlockingContext")
internal suspend fun SeekableByteChannel.writeToScope(
    writerScope: WriterScope,
    start: Long,
    endInclusive: Long
) {
    if (start > 0) {
        position(start)
    }

    if (endInclusive == -1L) {
        writerScope.channel.writeWhile { buffer ->
            val rc = read(buffer)
            rc != -1
        }

        return
    }

    var position = start
    writerScope.channel.writeWhile { buffer ->
        val fileRemaining = endInclusive - position + 1
        val rc = if (fileRemaining < buffer.remaining()) {
            val l = buffer.limit()
            buffer.limit(buffer.position() + fileRemaining.toInt())
            val r = read(buffer)
            buffer.limit(l)
            r
        } else {
            read(buffer)
        }

        if (rc > 0) position += rc

        rc != -1 && position <= endInclusive
    }
}

/**
 * Open a write channel for the file and launch a coroutine to read from it.
 * Please note that file writing is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code and freeze the whole application when runs on a pool that is not intended for blocking operations.
 * This is why [coroutineContext] should have [Dispatchers.IO] or
 * a coroutine dispatcher that is properly configured for blocking IO.
 */
@OptIn(DelicateCoroutinesApi::class)
public fun File.writeChannel(
    coroutineContext: CoroutineContext = Dispatchers.IO
): ByteWriteChannel = GlobalScope.reader(CoroutineName("file-writer") + coroutineContext, autoFlush = true) {
    @Suppress("BlockingMethodInNonBlockingContext")
    RandomAccessFile(this@writeChannel, "rw").use { file ->
        val copied = channel.copyTo(file.channel)
        file.setLength(copied) // truncate tail that could remain from the previously written data
    }
}.channel
