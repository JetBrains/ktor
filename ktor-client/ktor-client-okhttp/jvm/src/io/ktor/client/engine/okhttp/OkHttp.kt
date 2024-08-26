/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * A JVM/Android client engine that uses the OkHttp HTTP client.
 * This engine supports Android 5.0 and newer.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(OkHttp)
 * ```
 * To configure the engine, pass settings exposed by [OkHttpConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(OkHttp) {
 *     engine {
 *         // this: OkHttpConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
public object OkHttp : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine =
        OkHttpEngine(OkHttpConfig().apply(block))
}

public class OkHttpEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = OkHttp

    override fun toString(): String = "OkHttp"
}
