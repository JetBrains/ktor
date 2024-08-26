/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlin.coroutines.*

public abstract class NettyApplicationResponse(
    call: NettyApplicationCall,
    protected val context: ChannelHandlerContext,
    protected val engineContext: CoroutineContext,
    protected val userContext: CoroutineContext
) : BaseApplicationResponse(call) {

    /**
     * Promise set success when the response is ready to read or failed if a response is cancelled
     */
    internal val responseReady: ChannelPromise = context.newPromise()

    public lateinit var responseMessage: Any

    @Volatile
    protected var responseMessageSent: Boolean = false

    internal var responseChannel: ByteReadChannel = ByteReadChannel.Empty

    override suspend fun respondOutgoingContent(content: OutgoingContent) {
        try {
            super.respondOutgoingContent(content)
        } catch (t: Throwable) {
            val out = responseChannel as? ByteWriteChannel
            out?.close(t)
            throw t
        } finally {
            val out = responseChannel as? ByteWriteChannel
            out?.flushAndClose()
        }
    }

    override suspend fun respondFromBytes(bytes: ByteArray) {
        // Note that it shouldn't set HttpHeaders.ContentLength even if we know it here,
        // because it should've been set by commitHeaders earlier
        val chunked = headers[HttpHeaders.TransferEncoding] == "chunked"

        if (responseMessageSent) return

        val message = responseMessage(chunked, bytes)
        responseChannel = when (message) {
            is LastHttpContent -> ByteReadChannel.Empty
            else -> ByteReadChannel(bytes)
        }
        responseMessage = message
        responseReady.setSuccess()
        responseMessageSent = true
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        val channel = ByteChannel()
        val chunked = headers[HttpHeaders.TransferEncoding] == "chunked"
        sendResponse(chunked, content = channel)
        return channel
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        respondFromBytes(EmptyByteArray)
    }

    protected abstract fun responseMessage(chunked: Boolean, last: Boolean): Any

    /**
     * Returns http response object with [data] content
     */
    protected open fun responseMessage(chunked: Boolean, data: ByteArray): Any = responseMessage(chunked, true)

    /**
     * Returns http trailer message
     */
    internal open fun prepareTrailerMessage(): Any? {
        return null
    }

    internal fun sendResponse(chunked: Boolean = true, content: ByteReadChannel) {
        if (responseMessageSent) return

        responseChannel = content
        responseMessage = when {
            content.isClosedForRead -> {
                responseMessage(chunked = false, data = EmptyByteArray)
            }
            else -> {
                responseMessage(chunked, last = false)
            }
        }
        responseReady.setSuccess()
        responseMessageSent = true
    }

    internal fun ensureResponseSent() {
        sendResponse(content = ByteReadChannel.Empty)
    }

    internal fun close() {
        val existingChannel = responseChannel
        if (existingChannel is ByteWriteChannel) {
            existingChannel.close(ClosedWriteChannelException("Application response has been closed"))
            responseChannel = ByteReadChannel.Empty
        }

        ensureResponseSent()
        // we don't need to suspendAwait() here as it handled in NettyApplicationCall
        // while close only does flush() and doesn't terminate connection
    }

    public fun cancel() {
        if (!responseMessageSent) {
            responseChannel = ByteReadChannel.Empty
            responseReady.setFailure(java.util.concurrent.CancellationException("Response was cancelled"))
            responseMessageSent = true
        }
    }

    public companion object {
        private val EmptyByteArray = ByteArray(0)

        public val responseStatusCache: Array<HttpResponseStatus?> = HttpStatusCode.allStatusCodes
            .associateBy { it.value }.let { codes ->
                Array(1000) {
                    if (it in codes.keys) HttpResponseStatus(it, codes[it]!!.description) else null
                }
            }
    }
}
