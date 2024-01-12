/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class DelegatingTestClientEngine(
    override val config: DelegatingTestHttpClientConfig
) : HttpClientEngineBase("delegating-test-engine") {

    override val supportedCapabilities =
        setOf<HttpClientEngineCapability<*>>(WebSocketCapability, HttpTimeoutCapability)

    private val appEngine by lazy { config.testApplicationProvder().server.engine }
    private val externalEngines by lazy {
        val engines = mutableMapOf<String, TestHttpClientEngine>()
        config.testApplicationProvder().externalApplications.forEach { (authority, testApplication) ->
            engines[authority] = TestHttpClientEngine(
                TestHttpClientConfig().apply { app = testApplication.server.engine }
            )
        }
        engines.toMap()
    }
    private val mainEngine by lazy {
        TestHttpClientEngine(TestHttpClientConfig().apply { app = appEngine })
    }
    private val mainEngineHostWithPorts by lazy {
        runBlocking { appEngine.resolvedConnectors().map { "${it.host}:${it.port}" } }
    }

    private val clientJob: CompletableJob = Job(config.parentJob)

    override val coroutineContext: CoroutineContext = dispatcher + clientJob

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        config.testApplicationProvder().start()
        val authority = data.url.protocolWithAuthority
        val hostWithPort = data.url.hostWithPort
        return when {
            externalEngines.containsKey(authority) -> {
                externalEngines[authority]!!.execute(data)
            }

            hostWithPort in mainEngineHostWithPorts -> {
                mainEngine.execute(data)
            }

            else -> {
                throw InvalidTestRequestException(authority, externalEngines.keys, mainEngineHostWithPorts)
            }
        }
    }

    override fun close() {
        clientJob.complete()
        mainEngine.close()
        externalEngines.values.forEach { it.close() }
    }

    companion object : HttpClientEngineFactory<DelegatingTestHttpClientConfig> {
        override fun create(block: DelegatingTestHttpClientConfig.() -> Unit): HttpClientEngine {
            val config = DelegatingTestHttpClientConfig().apply(block)
            return DelegatingTestClientEngine(config)
        }
    }
}

/**
 * Thrown when a request is made to an unknown resource
 */
public class InvalidTestRequestException(
    authority: String,
    externalAuthorities: Set<String>,
    mainHostWithPorts: List<String>
) : IllegalArgumentException(
    "Can not resolve request to $authority. " +
        "Main app runs at ${mainHostWithPorts.joinToString()} and " +
        "external services are ${externalAuthorities.joinToString()}"
)

internal class DelegatingTestHttpClientConfig : HttpClientEngineConfig() {
    lateinit var testApplicationProvder: () -> TestApplication
    lateinit var parentJob: Job
}
