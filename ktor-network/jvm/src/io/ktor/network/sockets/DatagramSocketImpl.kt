/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.IOException
import java.nio.*
import java.nio.channels.*

internal class DatagramSocketImpl(
    override val channel: DatagramChannel,
    selector: SelectorManager
) : BoundDatagramSocket, ConnectedDatagramSocket, NIOSocketImpl<DatagramChannel>(
    channel,
    selector,
    DefaultDatagramByteBufferPool
) {
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

    private val sender: SendChannel<Datagram> = DatagramSendChannel(channel, this)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val receiver: ReceiveChannel<Datagram> = produce(Dispatchers.IO) {
        try {
            while (true) {
                channel.send(receiveImpl())
            }
        } catch (_: ClosedChannelException) {
        } catch (cause: IOException) {
        }
    }

    override val incoming: ReceiveChannel<Datagram>
        get() = receiver

    override val outgoing: SendChannel<Datagram>
        get() = sender

    override fun close() {
        receiver.cancel()
        super.close()
        sender.close()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun receiveImpl(): Datagram {
        val buffer = DefaultDatagramByteBufferPool.borrow()
        val address = try {
            channel.receive(buffer)
        } catch (cause: Throwable) {
            DefaultDatagramByteBufferPool.recycle(buffer)
            throw cause
        } ?: return receiveSuspend(buffer)

        interestOp(SelectInterest.READ, false)
        buffer.flip()
        val datagram = Datagram(buildPacket { writeFully(buffer) }, address.toSocketAddress())
        DefaultDatagramByteBufferPool.recycle(buffer)
        return datagram
    }

    private tailrec suspend fun receiveSuspend(buffer: ByteBuffer): Datagram {
        interestOp(SelectInterest.READ, true)
        selector.select(this, SelectInterest.READ)

        val address = try {
            channel.receive(buffer)
        } catch (cause: Throwable) {
            DefaultDatagramByteBufferPool.recycle(buffer)
            throw cause
        } ?: return receiveSuspend(buffer)

        interestOp(SelectInterest.READ, false)
        buffer.flip()
        val datagram = Datagram(buildPacket { writeFully(buffer) }, address.toSocketAddress())
        DefaultDatagramByteBufferPool.recycle(buffer)
        return datagram
    }
}
