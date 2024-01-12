/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import jakarta.servlet.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.*

/**
 * Servlet upgrade processing
 */
public interface ServletUpgrade {
    /**
     * Perform HTTP upgrade using engine's native API
     */
    public suspend fun performUpgrade(
        upgrade: OutgoingContent.ProtocolUpgrade,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    )
}

/**
 * The default servlet upgrade implementation using Servlet API.
 * Please note that some servlet containers may not support it or it may be broken.
 */
public object DefaultServletUpgrade : ServletUpgrade {
    @OptIn(InternalAPI::class)
    override suspend fun performUpgrade(
        upgrade: OutgoingContent.ProtocolUpgrade,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    ) {
        @Suppress("BlockingMethodInNonBlockingContext")
        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)

        val disableAsyncInput = servletRequest.servletContext?.serverInfo
            ?.contains("tomcat", ignoreCase = true) == true

        handler.up = UpgradeRequest(servletResponse, upgrade, engineContext, userContext, disableAsyncInput)
    }
}

// the following types need to be public as they are accessed through reflection

@InternalAPI
@Suppress("KDocMissingDocumentation")
public class UpgradeRequest(
    public val response: HttpServletResponse,
    public val upgradeMessage: OutgoingContent.ProtocolUpgrade,
    public val engineContext: CoroutineContext,
    public val userContext: CoroutineContext,
    public val disableAsyncInput: Boolean
)

private val ServletUpgradeCoroutineName = CoroutineName("servlet-upgrade")

// this class is instantiated by a servlet container
// so we can't pass [UpgradeRequest] through a constructor
// we also can't make it internal due to the same reason
@InternalAPI
@Suppress("KDocMissingDocumentation")
public class ServletUpgradeHandler : HttpUpgradeHandler, CoroutineScope {
    @Volatile
    public lateinit var up: UpgradeRequest

    @Volatile
    public lateinit var upgradeJob: CompletableJob

    override val coroutineContext: CoroutineContext get() = upgradeJob

    override fun init(webConnection: WebConnection?) {
        if (webConnection == null) {
            throw IllegalArgumentException("Upgrade processing requires WebConnection instance")
        }

        upgradeJob = Job(up.engineContext[Job])
        upgradeJob.invokeOnCompletion {
            webConnection.close()
        }

        val inputChannel = when {
            up.disableAsyncInput -> webConnection.inputStream.toByteReadChannel(
                context = up.userContext + upgradeJob,
                pool = KtorDefaultPool
            )
            else -> servletReader(webConnection.inputStream, Int.MAX_VALUE).channel
        }

        val outputChannel = servletWriter(webConnection.outputStream).channel

        launch(up.userContext + ServletUpgradeCoroutineName, start = CoroutineStart.UNDISPATCHED) {
            val job = up.upgradeMessage.upgrade(
                inputChannel,
                outputChannel,
                up.engineContext + upgradeJob,
                up.userContext + upgradeJob
            )

            upgradeJob.complete()
            job.invokeOnCompletion {
                inputChannel.cancel()
                outputChannel.close()
                upgradeJob.cancel()
            }
        }
    }

    override fun destroy() {
        try {
            upgradeJob.completeExceptionally(CancellationException("Upgraded WebConnection destroyed"))
        } catch (_: Throwable) {
        }
    }
}
