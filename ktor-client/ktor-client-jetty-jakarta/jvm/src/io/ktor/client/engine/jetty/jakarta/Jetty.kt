/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty.jakarta

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.utils.io.*

/**
 * A JVM client engine that uses the Jetty HTTP client.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Jetty)
 * ```
 * To configure the engine, pass settings exposed by [JettyEngineConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Jetty) {
 *     engine {
 *         // this: JettyEngineConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
public object Jetty : HttpClientEngineFactory<JettyEngineConfig> {
    override fun create(block: JettyEngineConfig.() -> Unit): HttpClientEngine =
        JettyHttp2Engine(JettyEngineConfig().apply(block))
}

@InternalAPI
public class JettyEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> = Jetty

    override fun toString(): String = "Jetty"
}
