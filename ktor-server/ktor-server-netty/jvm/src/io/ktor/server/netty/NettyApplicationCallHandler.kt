/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.http1.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

private const val CHUNKED_VALUE = "chunked"

internal class NettyApplicationCallHandler(
    userCoroutineContext: CoroutineContext,
    private val enginePipeline: EnginePipeline
) : ChannelInboundHandlerAdapter(), CoroutineScope {
    override val coroutineContext: CoroutineContext = userCoroutineContext

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ApplicationCall -> handleRequest(ctx, msg)
            else -> ctx.fireChannelRead(msg)
        }
    }

    private fun handleRequest(context: ChannelHandlerContext, call: ApplicationCall) {
        val callContext = CallHandlerCoroutineName + NettyDispatcher.CurrentContext(context)

        launch(callContext, start = CoroutineStart.UNDISPATCHED) {
            when {
                call is NettyHttp1ApplicationCall && !call.request.isValid() -> {
                    respondError400BadRequest(call)
                }
                else ->
                    try {
                        enginePipeline.execute(call)
                    } catch (error: Exception) {
                        handleFailure(call, error)
                    }
            }
        }
    }

    private suspend fun respondError400BadRequest(call: NettyHttp1ApplicationCall) {
        logCause(call)

        call.response.status(HttpStatusCode.BadRequest)
        call.response.headers.append(HttpHeaders.ContentLength, "0", safeOnly = false)
        call.response.headers.append(HttpHeaders.Connection, "close", safeOnly = false)
        call.response.sendResponse(chunked = false, ByteReadChannel.Empty)
        call.finish()
    }

    private fun logCause(call: NettyHttp1ApplicationCall) {
        if (call.application.log.isTraceEnabled) {
            val cause = call.request.httpRequest.decoderResult()?.cause() ?: return
            call.application.log.trace("Failed to decode request", cause)
        }
    }

    companion object {
        internal val CallHandlerCoroutineName = CoroutineName("call-handler")
    }
}

internal fun NettyHttp1ApplicationRequest.isValid(): Boolean {
    if (httpRequest.decoderResult().isFailure) {
        return false
    }

    if (!headers.contains(HttpHeaders.TransferEncoding)) return true

    val encodings = headers.getAll(HttpHeaders.TransferEncoding) ?: return true
    if (!encodings.hasValidTransferEncoding()) {
        return false
    }

    return true
}

internal fun List<String>.hasValidTransferEncoding(): Boolean {
    forEachIndexed { headerIndex, header ->
        val chunkedStart = header.indexOf(CHUNKED_VALUE)
        if (chunkedStart == -1) return@forEachIndexed

        if (chunkedStart > 0 && !header[chunkedStart - 1].isSeparator()) {
            return@forEachIndexed
        }

        val afterChunked: Int = chunkedStart + CHUNKED_VALUE.length
        if (afterChunked < header.length && !header[afterChunked].isSeparator()) {
            return@forEachIndexed
        }

        if (headerIndex != lastIndex) {
            return false
        }

        val chunkedIsNotLast = chunkedStart + CHUNKED_VALUE.length < header.length
        if (chunkedIsNotLast) {
            return false
        }
    }

    return true
}

private fun Char.isSeparator(): Boolean =
    (this == ' ' || this == ',')
