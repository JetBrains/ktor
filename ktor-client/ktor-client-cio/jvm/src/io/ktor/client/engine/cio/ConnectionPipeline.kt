/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.*
import java.nio.channels.*
import kotlin.coroutines.*

internal actual class ConnectionPipeline actual constructor(
    keepAliveTime: Long,
    pipelineMaxSize: Int,
    connection: Connection,
    overProxy: Boolean,
    tasks: Channel<RequestTask>,
    parentContext: CoroutineContext
) : CoroutineScope {
    actual override val coroutineContext: CoroutineContext = parentContext + Job()

    private val networkInput = connection.input
    private val networkOutput = connection.output
    private val requestLimit = Semaphore(pipelineMaxSize)
    private val responseChannel = Channel<ConnectionResponseTask>(Channel.UNLIMITED)

    public actual val pipelineContext: Job = launch(start = CoroutineStart.LAZY) {
        try {
            while (true) {
                val task = withTimeoutOrNull(keepAliveTime) {
                    tasks.receive()
                } ?: break

                try {
                    requestLimit.acquire()
                    responseChannel.send(ConnectionResponseTask(GMTDate(), task))
                } catch (cause: Throwable) {
                    task.response.completeExceptionally(cause)
                    throw cause
                }

                writeRequest(task.request, networkOutput, task.context, overProxy, closeChannel = false)
                networkOutput.flush()
            }
        } catch (_: ClosedChannelException) {
        } catch (_: ClosedReceiveChannelException) {
        } catch (_: CancellationException) {
        } finally {
            responseChannel.close()
            /**
             * Workaround bug with socket.close
             */
//            outputChannel.close()
        }
    }

    private val responseHandler = launch(start = CoroutineStart.LAZY) {
        try {
            var shouldClose = false
            for ((requestTime, task) in responseChannel) {
                requestLimit.release()
                try {
                    val rawResponse = parseResponse(networkInput)
                        ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")

                    val callContext = task.context
                    val callJob = callContext[Job]!!

                    val status = HttpStatusCode(rawResponse.status, rawResponse.statusText.toString())
                    val method = task.request.method
                    val contentLength = rawResponse.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
                    val transferEncoding = rawResponse.headers[HttpHeaders.TransferEncoding]
                    val chunked = transferEncoding == "chunked"
                    val connectionType = ConnectionOptions.parse(rawResponse.headers[HttpHeaders.Connection])

                    val headers = buildHeaders {
                        appendAll(CIOHeaders(rawResponse.headers))
                        rawResponse.headers.release()
                    }

                    val version = HttpProtocolVersion.parse(rawResponse.version)

                    shouldClose = (connectionType == null && version == HttpProtocolVersion.HTTP_1_0) ||
                        connectionType == ConnectionOptions.Close

                    val hasBody = (contentLength > 0 || chunked) &&
                        (method != HttpMethod.Head) &&
                        (status !in listOf(HttpStatusCode.NotModified, HttpStatusCode.NoContent)) &&
                        !status.isInformational()

                    val responseChannel = if (hasBody) ByteChannel() else null

                    var skipTask: Job? = null
                    val body: ByteReadChannel = if (responseChannel != null) {
                        val proxyChannel = ByteChannel()
                        skipTask = skipCancels(responseChannel, proxyChannel)
                        proxyChannel
                    } else ByteReadChannel.Empty

                    callJob.invokeOnCompletion {
                        body.cancel()
                    }

                    val response = HttpResponseData(status, requestTime, headers, version, body, callContext)
                    task.response.complete(response)

                    responseChannel?.use {
                        parseHttpBody(
                            version,
                            contentLength,
                            transferEncoding,
                            connectionType,
                            networkInput,
                            this
                        )
                    }

                    skipTask?.join()
                } catch (cause: Throwable) {
                    task.response.completeExceptionally(cause)
                }

                task.context[Job]?.join()

                if (shouldClose) break
            }
        } finally {
            networkOutput.close()
            connection.socket.close()
        }
    }

    init {
        pipelineContext.start()
        responseHandler.start()
    }
}

private fun CoroutineScope.skipCancels(
    input: ByteReadChannel,
    output: ByteWriteChannel
): Job = launch {
    try {
        HttpClientDefaultPool.useInstance { buffer ->
            while (true) {
                buffer.clear()

                val count = input.readAvailable(buffer)
                if (count < 0) break

                buffer.flip()
                try {
                    output.writeFully(buffer)
                } catch (_: Throwable) {
                    // Output channel has been canceled, discard remaining
                    input.discard()
                }
            }
        }
    } catch (cause: Throwable) {
        output.close(cause)
        throw cause
    } finally {
        output.close()
    }
}
