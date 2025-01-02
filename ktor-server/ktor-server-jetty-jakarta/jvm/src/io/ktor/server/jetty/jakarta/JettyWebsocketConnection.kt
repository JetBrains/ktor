/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.jetty.io.AbstractConnection
import org.eclipse.jetty.io.Connection
import org.eclipse.jetty.io.EndPoint
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// TODO: Needs sensible error handling
@InternalAPI
public class JettyWebsocketConnection(
    private val endpoint: EndPoint,
    override val coroutineContext: CoroutineContext
) : AbstractConnection(endpoint, coroutineContext.executor()),
    Connection.UpgradeTo,
    CoroutineScope {

    private val inputBuffer by lazy { ByteBuffer.allocate(8192) }
    private val outputBuffer by lazy { ByteBuffer.allocate(8192) }

    public val inputChannel: ByteChannel = ByteChannel(true)
    public val outputChannel: ByteChannel = ByteChannel(false)

    private val channel = Channel<Boolean>(Channel.RENDEZVOUS)

    init {
        // Input job
        launch {
            // TODO: Handle errors
            while (true) {

                fillInterested()
                channel.receive()

                inputBuffer.clear().flip()

                val read = endpoint.fill(inputBuffer)

                if (read > 0) {
                    inputChannel.writeFully(inputBuffer)
                } else if (read == -1) {
                    endpoint.close()
                }
            }
        }

        // Output job
        launch {
            // TODO: Handle errors
            while (true) {

                if (outputChannel.isClosedForRead) {
                    return@launch
                }
                val outputBytes = outputChannel.readAvailable(outputBuffer.rewind())

                if (outputBytes > -1) {
                    suspendCancellableCoroutine<Unit> {
                        endpoint.write(
                            object : Callback {
                                override fun succeeded() {
                                    it.resume(Unit)
                                }

                                override fun failed(cause: Throwable) {
                                    it.resumeWithException(ChannelWriteException(exception = cause))
                                }
                            },
                            outputBuffer.flip()
                        )
                    }
                } else {
                    outputChannel.close()
                    // bufferPool.recycle(outputBuffer)
                    return@launch
                }
            }
        }
    }

    // TODO: Handle errors
    override fun onFillInterestedFailed(cause: Throwable) {
        throw cause
    }

    override fun onFillable() {
        channel.trySend(true)
    }

    override fun onUpgradeTo(buffer: ByteBuffer?): Unit = TODO()
}

private fun CoroutineContext.executor(): Executor = object : Executor, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = this@executor

    override fun execute(command: Runnable?) {
        launch { command?.run() }
    }
}
