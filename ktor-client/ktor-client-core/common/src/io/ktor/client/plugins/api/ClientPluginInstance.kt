/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

/**
 * An instance of [ClientPlugin] that can be installed into [HttpClient].
 */
public class ClientPluginInstance<PluginConfig : Any> internal constructor(
    internal val config: PluginConfig,
    internal val name: String,
    internal val body: ClientPluginBuilder<PluginConfig>.() -> Unit
) : Closeable {

    private var onClose: () -> Unit = {}

    @InternalAPI
    public fun install(scope: HttpClient) {
        val pluginBuilder = ClientPluginBuilder(AttributeKey(name), scope, config).apply(body)
        this.onClose = pluginBuilder.onClose
        pluginBuilder.hooks.forEach { it.install(scope) }
    }

    override fun close() {
        onClose.invoke()
    }
}
