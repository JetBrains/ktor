/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Local test server url.
 */
const val TEST_SERVER: String = "http://127.0.0.1:8080"
const val TEST_WEBSOCKET_SERVER: String = "ws://127.0.0.1:8080"
const val HTTP_PROXY_SERVER: String = "http://127.0.0.1:8082"

/**
 * Perform test with selected client [engine].
 */
fun testWithEngine(
    engine: HttpClientEngine,
    block: suspend TestClientBuilder<*>.() -> Unit
) = testWithClient(HttpClient(engine), block)

/**
 * Perform test with selected [client].
 */
private fun testWithClient(
    client: HttpClient,
    block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
) = testSuspend {
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
fun <T : HttpClientEngineConfig> testWithEngine(
    factory: HttpClientEngineFactory<T>,
    loader: ClientLoader? = null,
    block: suspend TestClientBuilder<T>.() -> Unit
) = testSuspend {

    val builder = TestClientBuilder<T>().apply { block() }

    if (builder.dumpAfterDelay > 0 && loader != null) {
        GlobalScope.launch {
            delay(builder.dumpAfterDelay)
            loader.dumpCoroutines()
        }
    }

    concurrency(builder.concurrency) {  threadId ->
        repeat(builder.repeatCount) { attempt ->
            val client = HttpClient(factory, block = builder.config)

            client.use {
                builder.test(TestInfo(threadId, attempt), it)
            }

            try {
                val job = client.coroutineContext[Job]!!
                job.join()
            } catch (cause: Throwable) {
                client.cancel("Test failed", cause)
                throw cause
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

@InternalAPI
@Suppress("KDocMissingDocumentation")
class TestClientBuilder<T : HttpClientEngineConfig>(
    var config: HttpClientConfig<T>.() -> Unit = {},
    var test: suspend TestInfo.(client: HttpClient) -> Unit = {},
    var repeatCount: Int = 1,
    var dumpAfterDelay: Long = -1,
    var concurrency: Int = 1
)

@InternalAPI
@Suppress("KDocMissingDocumentation")
fun <T : HttpClientEngineConfig> TestClientBuilder<T>.config(block: HttpClientConfig<T>.() -> Unit): Unit {
    config = block
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
fun TestClientBuilder<*>.test(block: suspend TestInfo.(client: HttpClient) -> Unit): Unit {
    test = block
}
