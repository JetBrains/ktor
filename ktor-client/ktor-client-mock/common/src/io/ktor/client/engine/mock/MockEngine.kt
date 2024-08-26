/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.mock

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*

/**
 * [HttpClientEngine] for writing tests without network.
 */
public class MockEngine(override val config: MockEngineConfig) : HttpClientEngineBase("ktor-mock") {
    override val supportedCapabilities: Set<HttpClientEngineCapability<out Any>> = setOf(
        HttpTimeoutCapability,
        WebSocketCapability,
        WebSocketExtensionsCapability
    )

    private val mutex = SynchronizedObject()
    private val contextState: CompletableJob = Job()

    private val _requestsHistory: MutableList<HttpRequestData> = mutableListOf()
    private val _responseHistory: MutableList<HttpResponseData> = mutableListOf()

    private var invocationCount: Int = 0

    init {
        check(config.requestHandlers.size > 0) {
            "No request handler provided in [MockEngineConfig], please provide at least one."
        }
    }

    /**
     * History of executed requests.
     */
    public val requestHistory: List<HttpRequestData> get() = _requestsHistory

    /**
     * History of sent responses.
     */
    public val responseHistory: List<HttpResponseData> get() = _responseHistory

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val handler = synchronized(mutex) {
            if (invocationCount >= config.requestHandlers.size) error("Unhandled ${data.url}")
            val handler = config.requestHandlers[invocationCount]

            invocationCount += 1
            if (config.reuseHandlers) {
                invocationCount %= config.requestHandlers.size
            }

            handler
        }

        val response = withContext(dispatcher + callContext) {
            handler(MockRequestHandleScope(callContext), data)
        }

        synchronized(mutex) {
            _requestsHistory.add(data)
            _responseHistory.add(response)
        }

        return response
    }

    override fun close() {
        super.close()

        coroutineContext[Job]!!.invokeOnCompletion {
            contextState.complete()
        }
    }

    public companion object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply(block))

        /**
         * Create [MockEngine] instance with single request handler.
         */
        public operator fun invoke(
            handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
        ): MockEngine = MockEngine(
            MockEngineConfig().apply {
                requestHandlers.add(handler)
            }
        )
    }
}
