/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import jakarta.servlet.*
import jakarta.servlet.http.*
import kotlinx.coroutines.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

private val JettyCallHandlerCoroutineName = CoroutineName("jetty-call-handler")

private val JettyKtorCounter = AtomicLong()

private const val THREAD_KEEP_ALIVE_TIME = 1L

@OptIn(InternalAPI::class)
internal class JettyKtorHandler(
    val environment: ApplicationEnvironment,
    private val pipeline: () -> EnginePipeline,
    private val engineDispatcher: CoroutineDispatcher,
    configuration: JettyApplicationEngineBase.Configuration,
    private val applicationProvider: () -> Application
) : AbstractHandler(), CoroutineScope {
    private val environmentName = configuration.connectors.joinToString("-") { it.port.toString() }
    private val queue: BlockingQueue<Runnable> = LinkedBlockingQueue()
    private val executor = ThreadPoolExecutor(
        configuration.callGroupSize,
        configuration.callGroupSize * 8,
        THREAD_KEEP_ALIVE_TIME,
        TimeUnit.MINUTES,
        queue
    ) { r ->
        Thread(r, "ktor-jetty-$environmentName-${JettyKtorCounter.incrementAndGet()}")
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val multipartConfig = MultipartConfigElement(System.getProperty("java.io.tmpdir"))

    private val handlerJob = SupervisorJob(applicationProvider().parentCoroutineContext[Job])

    override val coroutineContext: CoroutineContext =
        applicationProvider().parentCoroutineContext + handlerJob + DefaultUncaughtExceptionHandler(environment.log)

    override fun destroy() {
        try {
            super.destroy()
            executor.shutdownNow()
        } finally {
            handlerJob.cancel()
        }
    }

    override fun handle(
        target: String,
        baseRequest: Request,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        try {
            val contentType = request.contentType
            if (contentType != null && contentType.startsWith("multipart/")) {
                baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfig)
                // TODO someone reported auto-cleanup issues so we have to check it
            }

            request.startAsync()?.apply {
                timeout = 0 // Overwrite any default non-null timeout to prevent multiple dispatches
            }
            baseRequest.isHandled = true

            launch(dispatcher + JettyCallHandlerCoroutineName) {
                val call = JettyApplicationCall(
                    applicationProvider(),
                    baseRequest,
                    request,
                    response,
                    engineContext = engineDispatcher,
                    userContext = dispatcher,
                    coroutineContext = this@launch.coroutineContext
                )

                try {
                    pipeline().execute(call)
                } catch (cancelled: CancellationException) {
                    response.sendErrorIfNotCommitted(HttpServletResponse.SC_GONE)
                } catch (channelFailed: ChannelIOException) {
                } catch (error: Throwable) {
                    logError(call, error)
                    if (!response.isCommitted) {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                } finally {
                    try {
                        request.asyncContext?.complete()
                    } catch (expected: IllegalStateException) {
                    }
                }
            }
        } catch (ex: Throwable) {
            environment.log.error("Application cannot fulfill the request", ex)
            response.sendErrorIfNotCommitted(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }

    private fun HttpServletResponse.sendErrorIfNotCommitted(status: Int) {
        if (!isCommitted) {
            sendError(status)
        }
    }
}
