/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import io.ktor.util.*
import io.ktor.utils.io.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val initHook = DarwinLegacy

/**
 * A Kotlin/Native client engine that targets Darwin-based operating systems
 * (such as macOS, iOS, tvOS, and so on) and uses `NSURLSession` internally.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Darwin)
 * ```
 * To configure the engine, pass settings exposed by [DarwinClientEngineConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Darwin) {
 *     engine {
 *         // this: DarwinClientEngineConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
@OptIn(InternalAPI::class)
public object DarwinLegacy : HttpClientEngineFactory<DarwinLegacyClientEngineConfig> {
    init {
        engines.append(this)
    }

    override fun create(block: DarwinLegacyClientEngineConfig.() -> Unit): HttpClientEngine =
        DarwinLegacyClientEngine(DarwinLegacyClientEngineConfig().apply(block))

    override fun toString(): String = "DarwinLegacy"
}
