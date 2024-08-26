/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class TCPSocketNative(
    private val descriptor: Int,
    private val selector: SelectorManager,
    val selectable: SelectableNative,
    override val remoteAddress: SocketAddress,
    override val localAddress: SocketAddress,
    parent: CoroutineContext = EmptyCoroutineContext
) : Socket, CoroutineScope {
    private val _context: CompletableJob = Job(parent[Job])

    override val coroutineContext: CoroutineContext = parent + Dispatchers.Unconfined + _context

    override val socketContext: Job
        get() = _context

    override fun attachForReading(channel: ByteChannel): WriterJob =
        attachForReadingImpl(channel, descriptor, selectable, selector)

    override fun attachForWriting(channel: ByteChannel): ReaderJob =
        attachForWritingImpl(channel, descriptor, selectable, selector)

    override fun close() {
        _context.complete()
        _context.invokeOnCompletion {
            ktor_shutdown(descriptor, ShutdownCommands.Both)
            // Descriptor is closed by the selector manager
            selector.notifyClosed(selectable)
        }
    }
}
