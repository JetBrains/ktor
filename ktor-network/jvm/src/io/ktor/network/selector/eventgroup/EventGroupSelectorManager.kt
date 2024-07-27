/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import io.ktor.network.selector.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.nio.channels.*
import java.nio.channels.spi.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
public class EventGroupSelectorManager(context: CoroutineContext) : SelectorManager {
    public val group: EventGroup = EventGroup(context.eventGroupParallelism())

    override val coroutineContext: CoroutineContext = context + CoroutineName("eventgroup")

    override val provider: SelectorProvider = SelectorProvider.provider()

    override fun notifyClosed(selectable: Selectable) {
       // whatever
    }

    override suspend fun select(selectable: Selectable, interest: SelectInterest) {
        error("no select in eventgroup")
    }

    override fun close() {
        group.close()
    }
}

public class EventGroup(private val maxLoops: Int) {
    private val acceptLoop = Eventloop()
    private val loopIndex = atomic(0)
    private val loops = mutableListOf<Eventloop>()

    init {
        acceptLoop.run()

        repeat(maxLoops - 1) {
            val next = Eventloop().apply { run() }
            loops.add(next)
        }
    }

    private fun registerAcceptKey(channel: Selectable) = acceptLoop.runOnLoop {
        acceptLoop.addInterest(channel, SelectionKey.OP_ACCEPT)
    }

    internal fun registerChannel(channel: ServerSocketChannel): RegisteredServerChannel {
        val selectableChannel = SelectableBase(channel)
        val key = registerAcceptKey(selectableChannel)

        return RegisteredServerChannelImpl(channel, key)
    }

    private inner class RegisteredServerChannelImpl(
        override val channel: ServerSocketChannel,
        private val key: CompletableDeferred<SelectionKey>,
    ) : RegisteredServerChannel {
        override suspend fun acceptConnection(configure: (SocketChannel) -> Unit): ConnectionImpl {
            val result = key.await().attachment.runTask(SelectionKey.OP_ACCEPT) {
                channel.accept().apply {
                    configureBlocking(false)
                    configure(this)
                }
            }

            val nextLoopIndex = loopIndex.getAndIncrement() % (maxLoops - 1)

            return ConnectionImpl(result, loops[nextLoopIndex])
        }
    }

    private class ConnectionImpl(
        override val channel: SocketChannel,
        val loop: Eventloop,
    ) : Connection {
        private val selectable = SelectableBase(channel)

        override suspend fun <T> performRead(body: suspend (SocketChannel) -> T): T {
            return runTask(SelectionKey.OP_READ) { body(channel) }
        }

        override suspend fun <T> performWrite(body: suspend (SocketChannel) -> T): T {
            return runTask(SelectionKey.OP_WRITE) { body(channel) }
        }

        override fun close() {
            channel.close()
        }

        private suspend fun <T> runTask(interest: Int, body: suspend () -> T): T {
            val key = loop.addInterest(selectable, interest)
            return key.attachment.runTask(interest, body).also {
                loop.deleteInterest(selectable, interest)
            }
        }
    }

    public fun close() {
        acceptLoop.close(null)
        loops.forEach { it.close(null) }
    }
}
