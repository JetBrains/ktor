/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.util.*
import kotlin.native.concurrent.*

internal val PLUGIN_INSTALLED_LIST = AttributeKey<Attributes>("ApplicationPluginRegistry")

/**
 * Base interface representing a [HttpClient] plugin.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.HttpClientPlugin)
 */
public interface HttpClientPlugin<out TConfig : Any, TPlugin : Any> {
    /**
     * The [AttributeKey] for this plugin.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.HttpClientPlugin.key)
     */
    public val key: AttributeKey<TPlugin>

    /**
     * Builds a [TPlugin] by calling the [block] with a [TConfig] config instance as receiver.
     */
    public fun prepare(block: TConfig.() -> Unit = {}): TPlugin

    /**
     * Installs the [plugin] class for a [HttpClient] defined at [scope].
     */
    public fun install(plugin: TPlugin, scope: HttpClient)
}

/**
 * Returns a [plugin] installed in this client. Returns `null` if the plugin was not previously installed.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.pluginOrNull)
 */
public fun <B : Any, F : Any> HttpClient.pluginOrNull(plugin: HttpClientPlugin<B, F>): F? =
    attributes.getOrNull(PLUGIN_INSTALLED_LIST)?.getOrNull(plugin.key)

/**
 * Returns a [plugin] installed in [HttpClient].
 *
 * @throws [IllegalStateException] if [plugin] is not installed.
 */
public fun <B : Any, F : Any> HttpClient.plugin(plugin: HttpClientPlugin<B, F>): F {
    return pluginOrNull(plugin) ?: throw IllegalStateException(
        "Plugin $plugin is not installed. Consider using `install(${plugin.key})` in client config first."
    )
}
