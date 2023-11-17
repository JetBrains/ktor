/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio.backend

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.cio.*
import io.ktor.server.cio.internal.*
import io.ktor.server.engine.*
import io.ktor.server.engine.internal.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*

/**
 * Start a http server with [settings] invoking [handler] for every request
 */
@OptIn(InternalAPI::class)
public fun CoroutineScope.httpServer(
    settings: HttpServerSettings,
    handler: HttpRequestHandler
): HttpServer {
    val selector = SelectorManager(coroutineContext)
    return httpServer(
        createServer = {
            aSocket(selector).tcp().bind(
                hostname = settings.host,
                port = settings.port
            ) {
                reuseAddress = settings.reuseAddress
            }
        },
        serverJobName = CoroutineName("server-root-${settings.port}"),
        acceptJobName = CoroutineName("accept-${settings.port}"),
        timeout = WeakTimeoutQueue(settings.connectionIdleTimeoutSeconds * 1000L),
        selector = selector,
        handler = handler
    )
}

/**
 * Start an http server with [settings] invoking [handler] for every request
 */
@InternalAPI
public fun CoroutineScope.httpServer(
    createServer: () -> ServerSocket,
    timeout: WeakTimeoutQueue,
    serverJobName: CoroutineName,
    acceptJobName: CoroutineName,
    selector: SelectorManager,
    handler: HttpRequestHandler
): HttpServer {
    val socket = CompletableDeferred<ServerSocket>()

    val serverLatch: CompletableJob = Job()

    val serverJob = launch(
        context = serverJobName,
        start = CoroutineStart.UNDISPATCHED
    ) {
        serverLatch.join()
    }

    val logger = KtorSimpleLogger(
        HttpServer::class.simpleName ?: HttpServer::class.qualifiedName ?: HttpServer::class.toString()
    )

    val acceptJob = launch(serverJob + acceptJobName) {
        createServer().use { server ->
            socket.complete(server)

            val exceptionHandler = coroutineContext[CoroutineExceptionHandler]
                ?: DefaultUncaughtExceptionHandler(logger)

            val connectionScope = CoroutineScope(
                coroutineContext +
                    SupervisorJob(serverJob) +
                    exceptionHandler +
                    CoroutineName("request")
            )

            try {
                while (true) {
                    val client: Socket = try {
                        server.accept()
                    } catch (cause: IOException) {
                        continue
                    }

                    val connection = ServerIncomingConnection(
                        client.openReadChannel(),
                        client.openWriteChannel(),
                        client.remoteAddress.toNetworkAddress(),
                        client.localAddress.toNetworkAddress()
                    )

                    val clientJob = connectionScope.startServerConnectionPipeline(
                        connection,
                        timeout,
                        handler
                    )

                    clientJob.invokeOnCompletion {
                        client.close()
                    }
                }
            } catch (closed: ClosedChannelException) {
                coroutineContext.cancel()
            } finally {
                server.close()
                server.awaitClosed()
                connectionScope.coroutineContext.cancel()
            }
        }
    }

    acceptJob.invokeOnCompletion { cause ->
        cause?.let { socket.completeExceptionally(it) }
        serverLatch.complete()
        timeout.process()
    }

    @OptIn(InternalCoroutinesApi::class) // TODO it's attach child?
    serverJob.invokeOnCompletion(onCancelling = true) {
        timeout.cancel()
    }
    serverJob.invokeOnCompletion {
        selector.close()
    }

    return HttpServer(serverJob, acceptJob, socket)
}
