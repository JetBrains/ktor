/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.posix.*
import kotlin.coroutines.*

internal class DatagramSocketNative(
    private val descriptor: Int,
    val selector: SelectorManager,
    private val remote: SocketAddress?,
    parent: CoroutineContext = EmptyCoroutineContext
) : BoundDatagramSocket, ConnectedDatagramSocket, Socket, CoroutineScope {
    private val _context: CompletableJob = Job(parent[Job])
    val selectable: SelectableNative = SelectableNative(descriptor)

    override val coroutineContext: CoroutineContext = parent + Dispatchers.Unconfined + _context

    override val socketContext: Job
        get() = _context

    override val localAddress: SocketAddress
        get() = getLocalAddress(descriptor).toSocketAddress()

    override val remoteAddress: SocketAddress
        get() = getRemoteAddress(descriptor).toSocketAddress()

    private val sender: SendChannel<Datagram> = DatagramSendChannel(descriptor, this, remote)

    override fun toString(): String = "DatagramSocketNative(descriptor=$descriptor)"

    @OptIn(ExperimentalCoroutinesApi::class)
    private val receiver: ReceiveChannel<Datagram> = produce {
        try {
            while (true) {
                val received = readDatagram()
                channel.send(received)
            }
        } catch (_: ClosedSendChannelException) {
        } catch (cause: IOException) {
        }
    }

    override val incoming: ReceiveChannel<Datagram>
        get() = receiver

    override val outgoing: SendChannel<Datagram>
        get() = sender

    override fun close() {
        receiver.cancel()
        _context.complete()
        _context.invokeOnCompletion {
            shutdown(descriptor, SHUT_RDWR)
            // Descriptor is closed by the selector manager
            selector.notifyClosed(selectable)
        }
        sender.close()
    }

    @Suppress("DEPRECATION")
    override fun attachForReading(channel: ByteChannel): WriterJob =
        attachForReadingImpl(channel, descriptor, selectable, selector)

    @Suppress("DEPRECATION")
    override fun attachForWriting(channel: ByteChannel): ReaderJob =
        attachForWritingImpl(channel, descriptor, selectable, selector)

    private suspend fun readDatagram(): Datagram {
        while (true) {
            val datagram = tryReadDatagram()
            if (datagram != null) return datagram
            selector.select(selectable, SelectInterest.READ)
        }
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    private fun tryReadDatagram(): Datagram? = memScoped {
        val clientAddress = alloc<sockaddr_storage>()
        val clientAddressLength: UIntVarOf<UInt> = alloc()
        clientAddressLength.value = sizeOf<sockaddr_storage>().convert()

        val buffer = DefaultDatagramChunkBufferPool.borrow()
        try {
            val count = buffer.write { memory, startIndex, endIndex ->
                val bufferStart = memory.pointer + startIndex
                val size = endIndex - startIndex
                val bytesRead = recvfrom(
                    descriptor,
                    bufferStart,
                    size.convert(),
                    0,
                    clientAddress.ptr.reinterpret(),
                    clientAddressLength.ptr
                ).toInt()

                when (bytesRead) {
                    0 -> throw IOException("Failed reading from closed socket")
                    -1 -> {
                        if (errno == EAGAIN) return@write 0
                        throw PosixException.forErrno()
                    }
                    else -> bytesRead
                }
            }

            if (count <= 0) return null
            val address = clientAddress.reinterpret<sockaddr>().toNativeSocketAddress()

            return Datagram(
                buildPacket { writeFully(buffer) },
                address.toSocketAddress()
            )
        } finally {
            buffer.release(DefaultDatagramChunkBufferPool)
        }
    }
}
