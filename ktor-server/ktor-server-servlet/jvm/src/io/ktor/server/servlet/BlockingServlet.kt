/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import javax.servlet.*
import javax.servlet.http.*
import kotlin.coroutines.*

internal class BlockingServletApplicationCall(
    application: Application,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    override val coroutineContext: CoroutineContext,
    managedByEngineHeaders: Set<String> = emptySet()
) : BaseApplicationCall(application), CoroutineScope {
    override val request: BaseApplicationRequest = BlockingServletApplicationRequest(this, servletRequest)
    override val response: BlockingServletApplicationResponse =
        BlockingServletApplicationResponse(this, servletResponse, coroutineContext, managedByEngineHeaders)

    init {
        putResponseAttribute()
        putServletAttributes(servletRequest)
    }
}

private class BlockingServletApplicationRequest(
    call: PipelineCall,
    servletRequest: HttpServletRequest
) : ServletApplicationRequest(call, servletRequest) {

    private val inputStreamChannel by lazy {
        servletRequest.inputStream.toByteReadChannel(context = UnsafeBlockingTrampoline, pool = KtorDefaultPool)
    }

    override val engineReceiveChannel: ByteReadChannel get() = inputStreamChannel
}

internal class BlockingServletApplicationResponse(
    call: PipelineCall,
    servletResponse: HttpServletResponse,
    override val coroutineContext: CoroutineContext,
    managedByEngineHeaders: Set<String> = emptySet()
) : ServletApplicationResponse(call, servletResponse, managedByEngineHeaders), CoroutineScope {
    override fun createResponseJob(): ReaderJob =
        reader(UnsafeBlockingTrampoline, autoFlush = false) {
            val buffer = ArrayPool.borrow()
            try {
                writeLoop(buffer, channel, servletResponse.outputStream)
            } finally {
                ArrayPool.recycle(buffer)
            }
        }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun writeLoop(buffer: ByteArray, from: ByteReadChannel, to: ServletOutputStream) {
        while (true) {
            val n = from.readAvailable(buffer)
            if (n < 0) break
            check(n > 0)
            try {
                to.write(buffer, 0, n)
                to.flush()
            } catch (cause: Throwable) {
                throw ChannelWriteException("Failed to write to ServletOutputStream", cause)
            }
        }
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        @Suppress("BlockingMethodInNonBlockingContext")
        servletResponse.sendError(501, "Upgrade is not supported in synchronous servlets")
    }
}

/**
 * Never do like this! Very special corner-case.
 */
private object UnsafeBlockingTrampoline : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}
