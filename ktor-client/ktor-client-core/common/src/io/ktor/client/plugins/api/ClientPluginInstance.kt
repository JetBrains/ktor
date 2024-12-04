/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

/**
 * An instance of [ClientPlugin] that can be installed into [HttpClient].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.api.ClientPluginInstance)
 */
public class ClientPluginInstance<PluginConfig : Any> internal constructor(
    private val key: AttributeKey<ClientPluginInstance<PluginConfig>>,
    private val config: PluginConfig,
    private val body: ClientPluginBuilder<PluginConfig>.() -> Unit
) : Closeable {

    private var onClose: () -> Unit = {}

    @InternalAPI
    public fun install(scope: HttpClient) {
        val pluginBuilder = ClientPluginBuilder(key, scope, config).apply(body)
        this.onClose = pluginBuilder.onClose
        pluginBuilder.hooks.forEach { it.install(scope) }
    }

    override fun close() {
        onClose.invoke()
    }
}
