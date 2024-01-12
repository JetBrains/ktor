/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.*
import java.net.http.*
import java.time.*
import java.time.temporal.*

public class JavaHttpEngine(override val config: JavaHttpConfig) : HttpClientEngineBase("ktor-java") {

    private val protocolVersion = config.protocolVersion

    public override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    private var httpClient: HttpClient? = null

    init {
        coroutineContext.job.invokeOnCompletion {
            httpClient = null
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val engine = getJavaHttpClient(data)
        val callContext = callContext()

        return try {
            if (data.isUpgradeRequest()) {
                engine.executeWebSocketRequest(callContext, data)
            } else {
                engine.executeHttpRequest(callContext, data) ?: throw kotlinx.coroutines.CancellationException(
                    "Request was cancelled"
                )
            }
        } catch (cause: Throwable) {
            callContext.cancel(CancellationException("Failed to execute request", cause))
            throw cause
        }
    }

    private fun getJavaHttpClient(data: HttpRequestData): HttpClient {
        return httpClient ?: synchronized(this) {
            httpClient ?: HttpClient.newBuilder().apply {
                version(protocolVersion)
                executor(dispatcher.asExecutor())

                apply(config.config)

                setupProxy()

                data.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutAttribute ->
                    timeoutAttribute.connectTimeoutMillis?.let {
                        if (!isTimeoutInfinite(it)) connectTimeout(Duration.ofMillis(it))
                    }
                }
            }.build().also {
                httpClient = it
            }
        }
    }

    private fun HttpClient.Builder.setupProxy() {
        val proxy = config.proxy ?: return

        when (val type = proxy.type()) {
            Proxy.Type.SOCKS,
            Proxy.Type.HTTP -> {
                val address = proxy.address()

                check(address is InetSocketAddress) {
                    "Only http proxy is supported for Java HTTP engine."
                }

                proxy(ProxySelector.of(address))
            }

            Proxy.Type.DIRECT -> proxy(HttpClient.Builder.NO_PROXY)
            else -> error("Java HTTP engine does not currently support $type proxies.")
        }
    }
}

internal fun isTimeoutInfinite(timeoutMs: Long, now: Instant = Instant.now()): Boolean {
    if (timeoutMs == HttpTimeoutConfig.INFINITE_TIMEOUT_MS) return true
    return try {
        // Check that timeout end date as the number of milliseconds can fit Long type
        now.plus(timeoutMs, ChronoUnit.MILLIS).toEpochMilli()
        false
    } catch (_: ArithmeticException) {
        true
    }
}
