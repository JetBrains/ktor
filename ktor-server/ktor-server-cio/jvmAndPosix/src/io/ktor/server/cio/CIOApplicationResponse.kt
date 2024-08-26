/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class CIOApplicationResponse(
    call: CIOApplicationCall,
    private val output: ByteWriteChannel,
    private val input: ByteReadChannel,
    private val engineDispatcher: CoroutineContext,
    private val userDispatcher: CoroutineContext,
    private val upgraded: CompletableDeferred<Boolean>?
) : BaseApplicationResponse(call) {
    private var statusCode: HttpStatusCode = HttpStatusCode.OK
    private val headersBuilder = HeadersBuilder()

    private var chunkedChannel: ByteWriteChannel? = null

    private var chunkedJob: Job? = null

    override val headers = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            headersBuilder.append(name, value)
        }

        override fun getEngineHeaderNames(): List<String> {
            return headersBuilder.names().toList()
        }

        override fun getEngineHeaderValues(name: String): List<String> {
            return headersBuilder.getAll(name).orEmpty()
        }
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        sendResponseMessage(false)
        return preparedBodyChannel()
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        sendResponseMessage(contentReady = false)

        try {
            val upgradedJob = upgrade.upgrade(input, output, engineDispatcher, userDispatcher)
            upgradedJob.join()
        } finally {
            output.flushAndClose()
            input.cancel()
        }
    }

    override suspend fun respondFromBytes(bytes: ByteArray) {
        sendResponseMessage(contentReady = true)
        val channel = preparedBodyChannel()
        return withContext(Dispatchers.Unconfined) {
            channel.writeFully(bytes)
            channel.flushAndClose()
        }
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        sendResponseMessage(contentReady = true)
        output.flushAndClose()
    }

    override suspend fun respondOutgoingContent(content: OutgoingContent) {
        if (content is OutgoingContent.ProtocolUpgrade) {
            upgraded?.complete(true) ?: throw IllegalStateException(
                "Unable to perform upgrade as it is not requested by the client: " +
                    "request should have Upgrade and Connection headers filled properly"
            )
        } else {
            upgraded?.complete(false)
        }

        super.respondOutgoingContent(content)
        chunkedChannel?.flushAndClose()
        chunkedJob?.join()
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        this.statusCode = statusCode
    }

    private suspend fun sendResponseMessage(contentReady: Boolean) {
        val builder = RequestResponseBuilder()
        try {
            builder.responseLine("HTTP/1.1", statusCode.value, statusCode.description)
            for (name in headersBuilder.names()) {
                for (value in headersBuilder.getAll(name)!!) {
                    builder.headerLine(name, value)
                }
            }
            builder.emptyLine()
            output.writePacket(builder.build())

            if (!contentReady) {
                output.flush()
            }
        } finally {
            builder.release()
        }
    }

    private suspend fun preparedBodyChannel(): ByteWriteChannel {
        val chunked = headers[HttpHeaders.TransferEncoding] == "chunked"
        if (!chunked) return output

        val encoderJob = encodeChunked(output, Dispatchers.Unconfined)
        val chunkedOutput = encoderJob.channel

        chunkedChannel = chunkedOutput
        chunkedJob = encoderJob.job

        return chunkedOutput
    }
}
