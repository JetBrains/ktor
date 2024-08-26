/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.*
import java.nio.channels.*

internal fun CoroutineScope.attachForWritingDirectImpl(
    channel: ByteChannel,
    nioChannel: WritableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): ReaderJob = reader(Dispatchers.IO + CoroutineName("cio-to-nio-writer"), channel) {
    selectable.interestOp(SelectInterest.WRITE, false)
    try {
        val timeout = if (socketOptions?.socketTimeout != null) {
            createTimeout("writing-direct", socketOptions.socketTimeout) {
                channel.close(SocketTimeoutException())
            }
        } else {
            null
        }

        while (!channel.isClosedForRead) {
            if (channel.availableForRead == 0) {
                channel.awaitContent()
                continue
            }

            var rc = 0
            channel.read { buffer ->
                while (buffer.hasRemaining()) {
                    timeout.withTimeout {
                        do {
                            rc = nioChannel.write(buffer)
                        } while (buffer.hasRemaining() && rc > 0)
                    }
                }
            }

            if (rc == 0) {
                selectable.interestOp(SelectInterest.WRITE, true)
                selector.select(selectable, SelectInterest.WRITE)
            }
        }

        timeout?.finish()
    } finally {
        selectable.interestOp(SelectInterest.WRITE, false)
        if (nioChannel is SocketChannel) {
            try {
                if (java7NetworkApisAvailable) {
                    nioChannel.shutdownOutput()
                } else {
                    nioChannel.socket().shutdownOutput()
                }
            } catch (ignore: ClosedChannelException) {
            }
        }
    }
}
