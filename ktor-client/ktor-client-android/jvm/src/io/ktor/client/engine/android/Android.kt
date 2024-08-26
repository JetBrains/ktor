/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.*
import io.ktor.client.engine.*

/**
 * A JVM/Android client engine that uses `HttpURLConnection` under the hood.
 * You can use this engine if your application targets old Android versions (1.x+).
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Android)
 * ```
 * To configure the engine, pass settings exposed by [AndroidEngineConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Android) {
 *     engine {
 *         // this: AndroidEngineConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
public object Android : HttpClientEngineFactory<AndroidEngineConfig> {
    override fun create(block: AndroidEngineConfig.() -> Unit): HttpClientEngine =
        AndroidClientEngine(AndroidEngineConfig().apply(block))
}

public class AndroidEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Android

    override fun toString(): String = "Android"
}
