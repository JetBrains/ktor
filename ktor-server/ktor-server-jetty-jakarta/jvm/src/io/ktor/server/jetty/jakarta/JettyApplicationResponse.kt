/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.CoroutineScope
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

internal val bufferPool = ByteBufferPool(bufferSize = 8096)
internal val emptyBuffer = ByteBuffer.allocate(0)

@InternalAPI
public class JettyApplicationResponse(
    call: PipelineCall,
    private val request: Request,
    private val response: Response,
    override val coroutineContext: CoroutineContext
) : BaseApplicationResponse(call), CoroutineScope {
    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            if (responseJob.isInitialized()) {
                responseJob.value.apply {
                    channel.flushAndClose()
                    join()
                }
            }
        }
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            response.headers.add(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = response.headers.fieldNamesCollection.toList()
        override fun getEngineHeaderValues(name: String): List<String> = response.headers.getValuesList(name)
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        val connection = request.connectionMetaData.connection
        val endpoint = connection.endPoint
        endpoint.idleTimeout = 6000 * 1000

        val websocketConnection = JettyWebsocketConnection(endpoint, coroutineContext)
        response.write(true, emptyBuffer, Callback.from { endpoint.upgrade(websocketConnection) })

        val upgradeJob = upgrade.upgrade(
            websocketConnection.inputChannel,
            websocketConnection.outputChannel,
            coroutineContext,
            coroutineContext,
        )

        upgradeJob.invokeOnCompletion {
            websocketConnection.inputChannel.close()
            websocketConnection.outputChannel.close()
        }

        upgradeJob.join()
    }

    private val responseJob: Lazy<ReaderJob> = lazy {
        reader {
            val buffer = bufferPool.borrow()

            while (channel.readAvailable(buffer) > -1) {
                response.write(false, buffer.flip(), Callback.from { buffer.rewind() })
            }

            response.write(true, emptyBuffer, Callback.from { bufferPool.recycle(buffer) })
        }
    }

    private val responseChannel = lazy { responseJob.value.channel }

    override suspend fun respondFromBytes(bytes: ByteArray) {
        val buffer = bufferPool.borrow()

        response.write(
            true,
            buffer.put(bytes).flip(),
            Callback.from {
                bufferPool.recycle(buffer)
            }
        )
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        response.write(true, emptyBuffer, Callback.NOOP)
    }

    override suspend fun responseChannel(): ByteWriteChannel = responseChannel.value

    override fun setStatus(statusCode: HttpStatusCode) {
        response.status = statusCode.value
    }
}
