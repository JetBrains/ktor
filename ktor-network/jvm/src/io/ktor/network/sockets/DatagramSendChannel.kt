/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import kotlinx.coroutines.sync.*
import java.nio.*
import java.nio.channels.*

private val CLOSED: (Throwable?) -> Unit = {}
private val CLOSED_INVOKED: (Throwable?) -> Unit = {}

internal class DatagramSendChannel(
    val channel: DatagramChannel,
    val socket: DatagramSocketImpl
) : SendChannel<Datagram> {
    private val onCloseHandler = atomic<((Throwable?) -> Unit)?>(null)
    private val closed = atomic(false)
    private val closedCause = atomic<Throwable?>(null)
    private val lock = Mutex()

    @DelicateCoroutinesApi
    override val isClosedForSend: Boolean
        get() = socket.isClosed

    override fun close(cause: Throwable?): Boolean {
        if (!closed.compareAndSet(false, true)) {
            return false
        }

        closedCause.value = cause

        if (!socket.isClosed) {
            socket.close()
        }

        closeAndCheckHandler()

        return true
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun trySend(element: Datagram): ChannelResult<Unit> {
        if (!lock.tryLock()) return ChannelResult.failure()

        var result = false

        try {
            DefaultDatagramByteBufferPool.useInstance { buffer ->
                element.packet.copy().readAvailable(buffer)
                result = channel.send(buffer, element.address.toJavaAddress()) == 0
            }
        } finally {
            lock.unlock()
        }

        if (result) {
            element.packet.release()
        }

        return ChannelResult.success(Unit)
    }

    override suspend fun send(element: Datagram) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                DefaultDatagramByteBufferPool.useInstance { buffer ->
                    element.writeMessageTo(buffer)

                    val rc = channel.send(buffer, element.address.toJavaAddress())
                    if (rc != 0) {
                        socket.interestOp(SelectInterest.WRITE, false)
                        return@useInstance
                    }

                    sendSuspend(buffer, element.address)
                }
            }
        }
    }

    private suspend fun sendSuspend(buffer: ByteBuffer, address: SocketAddress) {
        while (true) {
            socket.interestOp(SelectInterest.WRITE, true)
            socket.selector.select(socket, SelectInterest.WRITE)

            @Suppress("BlockingMethodInNonBlockingContext")
            // this is actually non-blocking invocation
            if (channel.send(buffer, address.toJavaAddress()) != 0) {
                socket.interestOp(SelectInterest.WRITE, false)
                break
            }
        }
    }

    override val onSend: SelectClause2<Datagram, SendChannel<Datagram>>
        get() = TODO("[DatagramSendChannel] doesn't support [onSend] select clause")

    @ExperimentalCoroutinesApi
    override fun invokeOnClose(handler: (cause: Throwable?) -> Unit) {
        if (onCloseHandler.compareAndSet(null, handler)) {
            return
        }

        if (onCloseHandler.value === CLOSED) {
            require(onCloseHandler.compareAndSet(CLOSED, CLOSED_INVOKED))
            handler(closedCause.value)
            return
        }

        failInvokeOnClose(onCloseHandler.value)
    }

    private fun closeAndCheckHandler() {
        while (true) {
            val handler = onCloseHandler.value
            if (handler === CLOSED_INVOKED) break
            if (handler == null) {
                if (onCloseHandler.compareAndSet(null, CLOSED)) break
                continue
            }

            require(onCloseHandler.compareAndSet(handler, CLOSED_INVOKED))
            handler(closedCause.value)
            break
        }
    }
}

private fun failInvokeOnClose(handler: ((cause: Throwable?) -> Unit)?) {
    val message = if (handler === CLOSED_INVOKED) {
        "Another handler was already registered and successfully invoked"
    } else {
        "Another handler was already registered: $handler"
    }

    throw IllegalStateException(message)
}

private fun Datagram.writeMessageTo(buffer: ByteBuffer) {
    packet.readAvailable(buffer)
    buffer.flip()
}
