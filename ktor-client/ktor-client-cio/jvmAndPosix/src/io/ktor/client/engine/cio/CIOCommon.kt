// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.engine.*

/**
 * An asynchronous coroutine-based engine that can be used on JVM, Android, and Kotlin/Native.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(CIO)
 * ```
 * To configure the engine, pass settings exposed by [CIOEngineConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     engine {
 *         // this: CIOEngineConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
public data object CIO : HttpClientEngineFactory<CIOEngineConfig> {
    init {
        addToLoader()
    }

    override fun create(block: CIOEngineConfig.() -> Unit): HttpClientEngine =
        CIOEngine(CIOEngineConfig().apply(block))
}
