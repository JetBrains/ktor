/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Client plugins factory.
 */
public interface ClientPlugin<PluginConfig : Any> : HttpClientPlugin<PluginConfig, ClientPluginInstance<PluginConfig>>

/**
 * Creates a [ClientPlugin] that can be installed into an [HttpClient].
 *
 * The example below creates a plugin that adds a custom header to every request:
 * ```
 * class CustomHeaderPluginConfig {
 *    var headerName: String = "X-Custom-Header"
 *    var headerValue: String = "Custom-Header-Value"
 * }
 * val CustomHeaderPlugin = createClientPlugin("CustomHeaderPlugin", ::CustomHeaderPluginConfig) {
 *     val headerName = pluginConfig.headerName
 *     val headerValue = pluginConfig.headerValue
 *
 *     onRequest { request, _ ->
 *        request.headers.append(headerName, headerValue)
 *     }
 * }
 *
 * client.install(CustomHeaderPlugin) {
 *   headerName = "X-Another-Header"
 *   headerValue = "Another-Header-Value"
 * }
 * ```
 *
 * @param name A name of a plugin that is used to get its instance.
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created.
 * Note that it may be modified later when a user of your plugin calls [HttpClientConfig.install].
 * @param body Allows you to define handlers ([onRequest], [onResponse], and so on) that
 * can modify the behaviour of an [HttpClient] where your plugin is installed.
 **/
public fun <PluginConfigT : Any> createClientPlugin(
    name: String,
    createConfiguration: () -> PluginConfigT,
    body: ClientPluginBuilder<PluginConfigT>.() -> Unit
): ClientPlugin<PluginConfigT> =
    object : ClientPlugin<PluginConfigT> {
        override val key: AttributeKey<ClientPluginInstance<PluginConfigT>> = AttributeKey(name)

        override fun prepare(block: PluginConfigT.() -> Unit): ClientPluginInstance<PluginConfigT> {
            val config = createConfiguration().apply(block)
            return ClientPluginInstance(config, name, body)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: ClientPluginInstance<PluginConfigT>, scope: HttpClient) {
            plugin.install(scope)
        }
    }

/**
 * Creates a [ClientPlugin] with empty config that can be installed into an [HttpClient].
 *
 * The example below creates a plugin that adds a custom header to every request:
 * ```
 * val CustomHeaderPlugin = createClientPlugin("CustomHeaderPlugin") {
 *     onRequest { request, _ ->
 *        request.headers.append("X-Custom-Header", "Custom-Header-Value")
 *     }
 * }
 *
 * client.install(CustomHeaderPlugin)
 * ```
 *
 * @param name A name of a plugin that is used to get its instance.
 * @param body Allows you to define handlers ([onRequest], [onResponse], and so on) that
 * can modify the behaviour of an [HttpClient] where your plugin is installed.
 **/
public fun createClientPlugin(
    name: String,
    body: ClientPluginBuilder<Unit>.() -> Unit
): ClientPlugin<Unit> = createClientPlugin(name, {}, body)
