/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING", "KDocMissingDocumentation")

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Web url for tests.
 */
const val TEST_SERVER: String = "http://127.0.0.1:8080"

/**
 * Websocket server url for tests.
 */
const val TEST_WEBSOCKET_SERVER: String = "ws://127.0.0.1:8080"

/**
 * Proxy server url for tests.
 */
const val TCP_SERVER: String = "http://127.0.0.1:8082"

/**
 * Perform test with selected client [engine].
 */
fun testWithEngine(
    engine: HttpClientEngine,
    timeoutMillis: Long = 60 * 1000L,
    block: suspend TestClientBuilder<*>.() -> Unit
) = testWithClient(HttpClient(engine), timeoutMillis, block)

/**
 * Perform test with selected [client].
 */
private fun testWithClient(
    client: HttpClient,
    timeout: Long,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) = testSuspend(timeoutMillis = timeout) {
    val builder = TestClientBuilder<HttpClientEngineConfig>().also { it.block() }

    concurrency(builder.concurrency) { threadId ->
        repeat(builder.repeatCount) { attempt ->
            @Suppress("UNCHECKED_CAST")
            client.config { builder.config(this as HttpClientConfig<HttpClientEngineConfig>) }
                .use { client -> builder.test(TestInfo(threadId, attempt), client) }
        }
    }

    client.engine.close()
}

/**
 * Perform test with selected client engine [factory].
 */
@OptIn(DelicateCoroutinesApi::class)
fun <T : HttpClientEngineConfig> testWithEngine(
    factory: HttpClientEngineFactory<T>,
    loader: ClientLoader? = null,
    timeoutMillis: Long = 60L * 1000L,
    block: suspend TestClientBuilder<T>.() -> Unit
) = testSuspend(timeoutMillis = timeoutMillis) {
    val builder = TestClientBuilder<T>().apply { block() }

    if (builder.dumpAfterDelay > 0 && loader != null) {
        GlobalScope.launch {
            delay(builder.dumpAfterDelay)
            loader.dumpCoroutines()
        }
    }

    concurrency(builder.concurrency) { threadId ->
        repeat(builder.repeatCount) { attempt ->
            val client = HttpClient(factory, block = builder.config)

            client.use {
                builder.test(TestInfo(threadId, attempt), it)
            }

            try {
                val job = client.coroutineContext[Job]!!
                while (job.isActive) {
                    yield()
                }
            } catch (cause: Throwable) {
                client.cancel("Test failed", cause)
                throw cause
            } finally {
                builder.after(client)
            }
        }
    }
}

private suspend fun concurrency(level: Int, block: suspend (Int) -> Unit) {
    coroutineScope {
        List(level) {
            async {
                block(it)
            }
        }.awaitAll()
    }
}

class TestClientBuilder<out T : HttpClientEngineConfig>(
    var config: HttpClientConfig<@UnsafeVariance T>.() -> Unit = {},
    var test: suspend TestInfo.(client: HttpClient) -> Unit = {},
    var after: suspend (client: HttpClient) -> Unit = {},
    var repeatCount: Int = 1,
    var dumpAfterDelay: Long = -1,
    var concurrency: Int = 1
)

fun <T : HttpClientEngineConfig> TestClientBuilder<T>.config(block: HttpClientConfig<T>.() -> Unit) {
    config = block
}

fun TestClientBuilder<*>.test(block: suspend TestInfo.(client: HttpClient) -> Unit) {
    test = block
}

fun TestClientBuilder<*>.after(block: suspend (client: HttpClient) -> Unit) { // ktlint-disable no-unit-return
    after = block
}
