/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.cio

import io.ktor.http.*
import io.ktor.server.netty.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.coroutines.*

private const val UNFLUSHED_LIMIT = 65536

/**
 * Contains methods for handling http request with Netty
 */
internal class NettyHttpResponsePipeline(
    private val context: ChannelHandlerContext,
    private val httpHandlerState: NettyHttpHandlerState,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    /**
     * True if there is unflushed written data in channel
     */
    private val isDataNotFlushed: AtomicBoolean = atomic(false)

    /**
     * Represents promise which is marked as success when the last read request is handled.
     * Marked as fail when last read request is failed.
     * Default value is success on purpose to start first request handle
     */
    private var previousCallHandled: ChannelPromise = context.newPromise().also {
        it.setSuccess()
    }

    /** Flush if all is true:
     * - there is some unflushed data
     * - nothing to read from the channel
     * - there are no active requests
     */
    internal fun flushIfNeeded() {
        if (
            isDataNotFlushed.value &&
            httpHandlerState.isChannelReadCompleted.value &&
            httpHandlerState.activeRequests.value == 0L
        ) {
            context.flush()
            isDataNotFlushed.compareAndSet(expect = true, update = false)
        }
    }

    internal fun processResponse(call: NettyApplicationCall) {
        call.previousCallFinished = previousCallHandled
        call.finishedEvent = context.newPromise()
        previousCallHandled = call.finishedEvent

        processElement(call)
    }

    private fun processElement(call: NettyApplicationCall) = setOnResponseReadyHandler(call) {
        try {
            handleRequestMessage(call)
        } catch (actualException: Throwable) {
            respondWithFailure(call, actualException)
        } finally {
            call.responseWriteJob.cancel()
        }
    }

    /**
     * Process [call] with [block] when the response is ready and previous call is successfully processed.
     * [call] won't be processed with [block] if previous call is failed.
     */
    private fun setOnResponseReadyHandler(call: NettyApplicationCall, block: () -> Unit) {
        call.response.responseReady.addListener responseFlag@{ responseFlagResult ->
            call.previousCallFinished.addListener waitPreviousCall@{ previousCallResult ->
                if (!previousCallResult.isSuccess) {
                    respondWithFailure(call, previousCallResult.cause())
                    return@waitPreviousCall
                }
                if (!responseFlagResult.isSuccess) {
                    respondWithFailure(call, responseFlagResult.cause())
                    return@waitPreviousCall
                }
                block.invoke()
            }
        }
    }

    private fun respondWithFailure(call: NettyApplicationCall, actualException: Throwable) {
        val t = when {
            actualException is IOException && actualException !is ChannelIOException ->
                ChannelWriteException(exception = actualException)
            else -> actualException
        }

        flushIfNeeded()
        call.response.responseChannel.cancel(t)
        call.responseWriteJob.cancel()
        call.response.cancel()
        call.dispose()
        call.finishedEvent.setFailure(t)
        context.close()
    }

    private fun respondWithUpgrade(call: NettyApplicationCall, responseMessage: Any): ChannelFuture {
        val future = context.write(responseMessage)
        call.upgrade(context)
        call.isByteBufferContent = true

        context.flush()
        isDataNotFlushed.compareAndSet(expect = true, update = false)
        return future
    }

    /**
     * Writes the [lastMessage] to the channel, schedules flush and close the channel if needed
     */
    private fun handleLastResponseMessage(
        call: NettyApplicationCall,
        lastMessage: Any?,
        lastFuture: ChannelFuture
    ) {
        val prepareForClose = call.isContextCloseRequired() &&
            (!call.request.keepAlive || call.response.isUpgradeResponse())

        val lastMessageFuture = if (lastMessage != null) {
            val future = context.write(lastMessage)
            isDataNotFlushed.compareAndSet(expect = false, update = true)
            future
        } else {
            null
        }

        httpHandlerState.onLastResponseMessage(context)
        call.finishedEvent.setSuccess()

        lastMessageFuture?.addListener {
            if (prepareForClose) {
                close(lastFuture)
                return@addListener
            }
        }
        if (prepareForClose) {
            close(lastFuture)
            return
        }
        scheduleFlush()
    }

    fun close(lastFuture: ChannelFuture) {
        context.flush()
        isDataNotFlushed.compareAndSet(expect = true, update = false)
        lastFuture.addListener {
            context.close()
        }
    }

    private fun scheduleFlush() {
        context.executor().execute {
            flushIfNeeded()
        }
    }

    /**
     * Gets the request message and decides how it should be handled
     */
    private fun handleRequestMessage(call: NettyApplicationCall) {
        val responseMessage = call.response.responseMessage
        val response = call.response

        val requestMessageFuture = if (response.isUpgradeResponse()) {
            respondWithUpgrade(call, responseMessage)
        } else {
            respondWithHeader(responseMessage)
        }

        if (responseMessage is FullHttpResponse) {
            return handleLastResponseMessage(call, null, requestMessageFuture)
        } else if (responseMessage is Http2HeadersFrame && responseMessage.isEndStream) {
            return handleLastResponseMessage(call, null, requestMessageFuture)
        }

        val responseChannel = response.responseChannel
        val bodySize = when {
            responseChannel === ByteReadChannel.Empty -> 0
            responseMessage is HttpResponse -> responseMessage.headers().getInt("Content-Length", -1)
            responseMessage is Http2HeadersFrame -> responseMessage.headers().getInt("content-length", -1)
            else -> -1
        }

        launch(context.executor().asCoroutineDispatcher(), start = CoroutineStart.UNDISPATCHED) {
            respondWithBodyAndTrailerMessage(
                call,
                response,
                bodySize,
                requestMessageFuture
            )
        }
    }

    /**
     * Writes response header to the channel and makes a flush
     * if client is waiting for the response header
     */
    private fun respondWithHeader(responseMessage: Any): ChannelFuture {
        return if (isHeaderFlushNeeded()) {
            val future = context.writeAndFlush(responseMessage)
            isDataNotFlushed.compareAndSet(expect = true, update = false)
            future
        } else {
            val future = context.write(responseMessage)
            isDataNotFlushed.compareAndSet(expect = false, update = true)
            future
        }
    }

    /**
     * True if client is waiting for response header, false otherwise
     */
    private fun isHeaderFlushNeeded(): Boolean {
        val activeRequestsValue = httpHandlerState.activeRequests.value
        return httpHandlerState.isChannelReadCompleted.value &&
            !httpHandlerState.isCurrentRequestFullyRead.value &&
            activeRequestsValue == 1L
    }

    /**
     * Writes response body of size [bodySize] and trailer message to the channel and makes flush if needed
     */
    private suspend fun respondWithBodyAndTrailerMessage(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        bodySize: Int,
        requestMessageFuture: ChannelFuture
    ) {
        try {
            when (bodySize) {
                0 -> respondWithEmptyBody(call, requestMessageFuture)
                in 1..65536 -> respondWithSmallBody(call, response, bodySize)
                -1 -> respondBodyWithFlushOnLimitOrEmptyChannel(call, response, requestMessageFuture)
                else -> respondBodyWithFlushOnLimit(call, response, requestMessageFuture)
            }
        } catch (actualException: Throwable) {
            respondWithFailure(call, actualException)
        }
    }

    /**
     * Writes trailer message to the channel and makes flush if needed when response body is empty.
     */
    private fun respondWithEmptyBody(call: NettyApplicationCall, lastFuture: ChannelFuture) {
        return handleLastResponseMessage(call, call.prepareEndOfStreamMessage(false), lastFuture)
    }

    /**
     * Writes body and trailer message to the channel if response body size is up to 65536 bytes.
     * Makes flush if needed.
     */
    private suspend fun respondWithSmallBody(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        size: Int
    ) {
        val buffer = context.alloc().buffer(size)
        val channel = response.responseChannel
        val start = buffer.writerIndex()

        channel.readFully(buffer.nioBuffer(start, buffer.writableBytes()))
        buffer.writerIndex(start + size)

        val future = context.write(call.prepareMessage(buffer, true))
        isDataNotFlushed.compareAndSet(expect = false, update = true)

        val lastMessage = response.prepareTrailerMessage() ?: call.prepareEndOfStreamMessage(true)

        handleLastResponseMessage(call, lastMessage, future)
    }

    /**
     * Writes body and trailer message to the channel if body size is more than 65536 bytes.
     * Makes flush only when limit of written bytes is reached.
     */
    private suspend fun respondBodyWithFlushOnLimit(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        requestMessageFuture: ChannelFuture
    ) = respondWithBigBody(call, response, requestMessageFuture) { _, unflushedBytes ->
        unflushedBytes >= UNFLUSHED_LIMIT
    }

    /**
     * Writes body and trailer message to the channel if body size is unknown.
     * Makes flush when there is nothing to read from the response channel or
     * limit of written bytes is reached.
     */
    private suspend fun respondBodyWithFlushOnLimitOrEmptyChannel(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        requestMessageFuture: ChannelFuture
    ) = respondWithBigBody(call, response, requestMessageFuture) { channel, unflushedBytes ->
        unflushedBytes >= UNFLUSHED_LIMIT || channel.availableForRead == 0
    }

    private suspend fun respondWithBigBody(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        requestMessageFuture: ChannelFuture,
        shouldFlush: ShouldFlush
    ) {
        val channel = response.responseChannel

        var unflushedBytes = 0
        var lastFuture: ChannelFuture = requestMessageFuture

        @Suppress("DEPRECATION")
        channel.lookAheadSuspend {
            while (true) {
                val buffer = request(0, 1)
                if (buffer == null) {
                    if (!awaitAtLeast(1)) break
                    continue
                }

                val rc = buffer.remaining()
                val buf = context.alloc().buffer(rc)
                val idx = buf.writerIndex()
                buf.setBytes(idx, buffer)
                buf.writerIndex(idx + rc)

                consumed(rc)
                unflushedBytes += rc

                val message = call.prepareMessage(buf, false)

                if (shouldFlush.invoke(channel, unflushedBytes)) {
                    context.read()
                    val future = context.writeAndFlush(message)
                    isDataNotFlushed.compareAndSet(expect = true, update = false)
                    lastFuture = future
                    future.suspendAwait()
                    unflushedBytes = 0
                } else {
                    lastFuture = context.write(message)
                    isDataNotFlushed.compareAndSet(expect = false, update = true)
                }
            }
        }

        val lastMessage = response.prepareTrailerMessage() ?: call.prepareEndOfStreamMessage(false)
        handleLastResponseMessage(call, lastMessage, lastFuture)
    }
}

private fun NettyApplicationResponse.isUpgradeResponse() =
    status()?.value == HttpStatusCode.SwitchingProtocols.value

public class NettyResponsePipelineException(message: String) : Exception(message)

internal fun interface ShouldFlush {
    fun invoke(channel: ByteReadChannel, unflushedBytes: Int): Boolean
}
