/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.*
import javax.servlet.http.*
import kotlin.coroutines.*

public open class AsyncServletApplicationCall(
    application: Application,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    engineContext: CoroutineContext,
    userContext: CoroutineContext,
    upgrade: ServletUpgrade,
    parentCoroutineContext: CoroutineContext,
    managedByEngineHeaders: Set<String> = emptySet()
) : BaseApplicationCall(application), CoroutineScope {

    override val coroutineContext: CoroutineContext = parentCoroutineContext

    override val request: AsyncServletApplicationRequest =
        AsyncServletApplicationRequest(this, servletRequest, parentCoroutineContext + engineContext)

    override val response: ServletApplicationResponse by lazy {
        AsyncServletApplicationResponse(
            this,
            servletRequest,
            servletResponse,
            engineContext,
            userContext,
            upgrade,
            parentCoroutineContext + engineContext,
            managedByEngineHeaders
        ).also {
            putResponseAttribute(it)
        }
    }

    init {
        putServletAttributes(servletRequest)
    }
}

public class AsyncServletApplicationRequest(
    call: PipelineCall,
    servletRequest: HttpServletRequest,
    override val coroutineContext: CoroutineContext
) : ServletApplicationRequest(call, servletRequest), CoroutineScope {

    private var upgraded = false

    private val inputStreamChannel by lazy {
        if (!upgraded) {
            val contentLength = servletRequest.contentLength
            servletReader(servletRequest.inputStream, contentLength).channel
        } else ByteReadChannel.Empty
    }

    override val engineReceiveChannel: ByteReadChannel get() = inputStreamChannel

    internal fun upgraded() {
        upgraded = true
    }
}

public open class AsyncServletApplicationResponse(
    call: AsyncServletApplicationCall,
    protected val servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    private val engineContext: CoroutineContext,
    private val userContext: CoroutineContext,
    private val servletUpgradeImpl: ServletUpgrade,
    override val coroutineContext: CoroutineContext,
    managedByEngineHeaders: Set<String> = emptySet()
) : ServletApplicationResponse(call, servletResponse, managedByEngineHeaders), CoroutineScope {
    override fun createResponseJob(): ReaderJob =
        servletWriter(servletResponse.outputStream)

    public final override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        try {
            servletResponse.flushBuffer()
        } catch (e: IOException) {
            throw ChannelWriteException("Cannot write HTTP upgrade response", e)
        }

        (call.request as AsyncServletApplicationRequest).upgraded()
        completed = true

        servletUpgradeImpl.performUpgrade(upgrade, servletRequest, servletResponse, engineContext, userContext)
    }

    @UseHttp2Push
    override fun push(builder: ResponsePushBuilder) {
        if (!tryPush(servletRequest, builder)) {
            super.push(builder)
        }
    }

    @UseHttp2Push
    private fun tryPush(request: HttpServletRequest, builder: ResponsePushBuilder): Boolean {
        return foundPushImpls.any { function ->
            tryInvoke(function, request, builder)
        }
    }

    public companion object {
        private val foundPushImpls by lazy {
            listOf("io.ktor.servlet.v4.PushKt.doPush").mapNotNull { tryFind(it) }
        }

        private fun tryFind(spec: String): Method? = try {
            require("." in spec)
            val methodName = spec.substringAfterLast(".")

            Class.forName(spec.substringBeforeLast(".")).methods.singleOrNull { it.name == methodName }
        } catch (ignore: ReflectiveOperationException) {
            null
        } catch (ignore: LinkageError) {
            null
        }

        @UseHttp2Push
        private fun tryInvoke(function: Method, request: HttpServletRequest, builder: ResponsePushBuilder) = try {
            function.invoke(null, request, builder) as Boolean
        } catch (ignore: ReflectiveOperationException) {
            false
        } catch (ignore: LinkageError) {
            false
        }
    }
}
