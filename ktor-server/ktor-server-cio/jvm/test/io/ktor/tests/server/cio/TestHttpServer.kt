/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.http.cio.*
import io.ktor.network.util.*
import io.ktor.server.cio.*
import io.ktor.server.cio.backend.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.time.*
import io.ktor.utils.io.ByteChannel as KtorByteChannel

// this is only suitable for tests, do not use in production
@Suppress("BlockingMethodInNonBlockingContext")
@OptIn(DelicateCoroutinesApi::class)
internal fun testHttpServer(
    port: Int = 9096,
    ioCoroutineContext: CoroutineContext,
    callDispatcher: CoroutineContext,
    handler: HttpRequestHandler
): Pair<Job, Deferred<ServerSocketChannel>> {
    val deferred = CompletableDeferred<ServerSocketChannel>()
    val j = Job()

    // blocking acceptor
    GlobalScope.launch(ioCoroutineContext) {
        val server = ServerSocketChannel.open()!!
        server.bind(InetSocketAddress(port))
        deferred.complete(server)

        val live = ConcurrentHashMap<SocketChannel, Unit>()

        try {
            while (true) {
                val client = server.accept() ?: break
                live[client] = Unit
                client(client, ioCoroutineContext, callDispatcher, handler)
            }
        } catch (expected: ClosedChannelException) {
        } finally {
            server.close()
            val clients = live.keys.toList()
            live.keys.clear()
            clients.forEach {
                it.close()
            }
        }
    }

    j.invokeOnCompletion {
        deferred.invokeOnCompletion { t ->
            if (t == null) {
                @OptIn(ExperimentalCoroutinesApi::class)
                deferred.getCompleted().close()
            }
        }
    }

    return Pair(j, deferred)
}

@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
private suspend fun client(
    socket: SocketChannel,
    ioCoroutineContext: CoroutineContext,
    callDispatcher: CoroutineContext,
    handler: HttpRequestHandler
) {
    val incoming = io.ktor.utils.io.ByteChannel(true)
    val outgoing = KtorByteChannel()

    GlobalScope.launch(ioCoroutineContext) {
        val buffer = DefaultByteBufferPool.borrow()

        try {
            while (true) {
                buffer.clear()
                val rc = outgoing.readAvailable(buffer)
                if (rc == -1) break

                buffer.flip()
                while (buffer.hasRemaining()) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    socket.write(buffer)
                }
            }
        } finally {
            DefaultByteBufferPool.recycle(buffer)
        }
    }

    GlobalScope.launch(ioCoroutineContext) {
        val buffer = DefaultByteBufferPool.borrow()

        try {
            while (true) {
                buffer.clear()
                @Suppress("BlockingMethodInNonBlockingContext")
                val rc = socket.read(buffer)
                if (rc == -1) break

                buffer.flip()
                incoming.writeFully(buffer)
            }
        } catch (t: Throwable) {
            incoming.close(t)
        } finally {
            incoming.close()
            outgoing.close()
            DefaultByteBufferPool.recycle(buffer)
        }
    }

    CoroutineScope(ioCoroutineContext + Dispatchers.Unconfined).startServerConnectionPipeline(
        ServerIncomingConnection(
            incoming,
            outgoing,
            socket.remoteAddress,
            socket.localAddress
        ),
        timeout = Duration.INFINITE
    ) { request: Request ->
        val requestScope = this

        withContext(callDispatcher) {
            handler(requestScope.withContext(callDispatcher), request)
        }
    }.invokeOnCompletion {
        incoming.close()
        outgoing.close()
    }
}
