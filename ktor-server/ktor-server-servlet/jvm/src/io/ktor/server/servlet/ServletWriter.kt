/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.util.concurrent.*
import javax.servlet.*

internal fun CoroutineScope.servletWriter(output: ServletOutputStream): ReaderJob {
    val writer = ServletWriter(output)
    return reader(Dispatchers.IO, writer.channel) {
        writer.run()
    }
}

internal val ArrayPool = object : DefaultPool<ByteArray>(1024) {
    override fun produceInstance() = ByteArray(4096)
    override fun validateInstance(instance: ByteArray) {
        if (instance.size != 4096) {
            throw IllegalArgumentException(
                "Tried to recycle wrong ByteArray instance: most likely it hasn't been borrowed from this pool"
            )
        }
    }
}

private const val MAX_COPY_SIZE = 512 * 1024 // 512K

private class ServletWriter(val output: ServletOutputStream) : WriteListener {
    val channel = ByteChannel()

    private val events = Channel<Unit>(2)

    suspend fun run() {
        try {
            output.setWriteListener(this)
            events.receive()
            loop()

            finish()
        } catch (t: Throwable) {
            onError(t)
        } finally {
            events.close()
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun finish() {
        awaitReady()
        output.flush()
        awaitReady()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun loop() {
        if (channel.availableForRead == 0) {
            awaitReady()
            output.flush()
        }

        var copied = 0L
        while (!channel.isClosedForRead) {
            channel.read { buffer, start, end ->
                val rc = end - start
                copied += rc
                if (copied > MAX_COPY_SIZE) {
                    copied = 0
                    yield()
                }

                awaitReady()
                output.write(buffer, 0, rc)
                awaitReady()
                rc
            }
            if (channel.availableForRead == 0) output.flush()
        }
    }

    private suspend fun awaitReady() {
        if (output.isReady) return
        return awaitReadySuspend()
    }

    private suspend fun awaitReadySuspend() {
        do {
            events.receive()
        } while (!output.isReady)
    }

    override fun onWritePossible() {
        try {
            if (!events.trySend(Unit).isSuccess) {
                events.trySendBlocking(Unit)
            }
        } catch (ignore: Throwable) {
        }
    }

    override fun onError(t: Throwable) {
        val wrapped = wrapException(t)
        events.close(wrapped)
        channel.cancel(wrapped)
    }

    private fun wrapException(cause: Throwable): Throwable {
        return if (cause is IOException || cause is TimeoutException) {
            ChannelWriteException("Failed to write to servlet async stream", exception = cause)
        } else {
            cause
        }
    }
}
