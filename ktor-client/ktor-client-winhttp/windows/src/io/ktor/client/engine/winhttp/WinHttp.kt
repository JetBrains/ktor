/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.engine.*
import io.ktor.utils.io.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val initHook = WinHttp

/**
 * A Kotlin/Native client engine that targets Windows-based operating systems.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(WinHttp)
 * ```
 * To configure the engine, pass settings exposed by [WinHttpClientEngineConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(WinHttp) {
 *     engine {
 *         // this: WinHttpClientEngineConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
@OptIn(InternalAPI::class)
public object WinHttp : HttpClientEngineFactory<WinHttpClientEngineConfig> {
    init {
        engines.append(this)
    }

    override fun create(block: WinHttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return WinHttpClientEngine(WinHttpClientEngineConfig().apply(block))
    }

    override fun toString(): String {
        return "WinHttp"
    }
}
