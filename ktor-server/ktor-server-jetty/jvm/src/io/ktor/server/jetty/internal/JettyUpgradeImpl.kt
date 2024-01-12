/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.internal

import io.ktor.http.content.*
import io.ktor.server.servlet.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*
import java.util.concurrent.*
import javax.servlet.http.*
import kotlin.coroutines.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
public object JettyUpgradeImpl : ServletUpgrade {
    override suspend fun performUpgrade(
        upgrade: OutgoingContent.ProtocolUpgrade,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    ) {
        // Jetty doesn't support Servlet API's upgrade, so we have to implement our own

        val connection = servletRequest.getAttribute(HttpConnection::class.qualifiedName) as Connection
        val endPoint = connection.endPoint

        // for upgraded connections IDLE timeout should be significantly increased
        endPoint.idleTimeout = TimeUnit.MINUTES.toMillis(60L)

        withContext(engineContext + CoroutineName("upgrade-scope")) {
            try {
                coroutineScope {
                    val inputChannel = ByteChannel(autoFlush = true)
                    val reader = EndPointReader(endPoint, coroutineContext, inputChannel)
                    val writer = endPointWriter(endPoint)
                    val outputChannel = writer.channel

                    servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, reader)
                    if (endPoint is AbstractEndPoint) {
                        endPoint.upgrade(reader)
                    }
                    val upgradeJob = upgrade.upgrade(
                        inputChannel,
                        outputChannel,
                        coroutineContext,
                        coroutineContext + userContext
                    )

                    upgradeJob.invokeOnCompletion {
                        inputChannel.cancel()
                        outputChannel.close()
                        cancel()
                    }
                }
            } finally {
                connection.close()
            }
        }
    }
}
