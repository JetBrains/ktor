/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*

/**
 * A JavaScript client engine that uses the fetch API to execute requests.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Js)
 * ```
 * You can also call the [JsClient] function to get the [Js] engine singleton:
 * ```kotlin
 * val client = JsClient()
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
public actual object Js : HttpClientEngineFactory<JsClientEngineConfig> {
    override fun create(block: JsClientEngineConfig.() -> Unit): HttpClientEngine =
        JsClientEngine(JsClientEngineConfig().apply(block))
}

/** Configuration for the [Js] client. */
public actual open class JsClientEngineConfig : HttpClientEngineConfig() {
    /**
     * An `Object` which can contain additional configuration options that should get passed to node-fetch.
     *
     * For example, this can be used to configure a custom `Agent`:
     *
     * ```kotlin
     * HttpClient(Js) {
     *     engine {
     *         val agentOptions = js("Object").create(null)
     *         agentOptions.minVersion = "TLSv1.2"
     *         agentOptions.maxVersion = "TLSv1.3"
     *         nodeOptions.agent = Agent(agentOptions)
     *     }
     * }
     * ```
     */
    public var nodeOptions: dynamic = js("Object").create(null)
}
