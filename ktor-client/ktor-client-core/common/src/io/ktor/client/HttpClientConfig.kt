/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlin.collections.set

/**
 * A mutable [HttpClient] configuration.
 * Learn more about the client's configuration from
 * [Creating and configuring a client](https://ktor.io/docs/create-client.html).
 */
@KtorDsl
public class HttpClientConfig<T : HttpClientEngineConfig> {
    private val plugins: MutableMap<AttributeKey<*>, (HttpClient) -> Unit> = mutableMapOf()
    private val pluginConfigurations: MutableMap<AttributeKey<*>, Any.() -> Unit> = mutableMapOf()
    private val customInterceptors: MutableMap<String, (HttpClient) -> Unit> = mutableMapOf()

    internal var engineConfig: T.() -> Unit = {}

    /**
     * Allows you to configure engine parameters.
     *
     * You can learn more from [Engines](https://ktor.io/docs/http-client-engines.html).
     */
    public fun engine(block: T.() -> Unit) {
        val oldConfig = engineConfig
        engineConfig = {
            oldConfig()
            block()
        }
    }

    /**
     * Specifies whether the client redirects to URLs provided in the `Location` header.
     * You can disable redirections by setting this property to `false`.
     */
    public var followRedirects: Boolean = true

    /**
     * Uses [defaultTransformers] to automatically handle simple [ContentType].
     */
    public var useDefaultTransformers: Boolean = true

    /**
     * Terminates [HttpClient.receivePipeline] if the status code is not successful (>=300).
     * Learn more from [Response validation](https://ktor.io/docs/response-validation.html).
     */
    public var expectSuccess: Boolean = false

    /**
     * Indicates whether the client should use [development mode](https://ktor.io/docs/development-mode.html).
     * In development mode, the client's pipelines have advanced stack traces.
     */
    public var developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE

    /**
     * Installs the specified [plugin] and optionally configures it using the [configure] block.
     * Learn more from [Plugins](https://ktor.io/docs/http-client-plugins.html).
     */
    public fun <TBuilder : Any, TPlugin : Any> install(
        plugin: HttpClientPlugin<TBuilder, TPlugin>,
        configure: TBuilder.() -> Unit = {}
    ) {
        val previousConfigBlock = pluginConfigurations[plugin.key]
        pluginConfigurations[plugin.key] = {
            previousConfigBlock?.invoke(this)

            @Suppress("UNCHECKED_CAST")
            (this as TBuilder).configure()
        }

        if (plugins.containsKey(plugin.key)) return

        plugins[plugin.key] = { scope ->
            val attributes = scope.attributes.computeIfAbsent(PLUGIN_INSTALLED_LIST) { Attributes(concurrent = true) }
            val config = scope.config.pluginConfigurations[plugin.key]!!
            val pluginData = plugin.prepare(config)

            plugin.install(pluginData, scope)
            attributes.put(plugin.key, pluginData)
        }
    }

    /**
     * Installs an interceptor defined by [block].
     * The [key] parameter is used as a unique name, that also prevents installing duplicated interceptors.
     */
    public fun install(key: String, block: HttpClient.() -> Unit) {
        customInterceptors[key] = block
    }

    /**
     * Applies all the installed [plugins] and [customInterceptors] from this configuration
     * into the specified [client].
     */
    public fun install(client: HttpClient) {
        plugins.values.forEach { client.apply(it) }
        customInterceptors.values.forEach { client.apply(it) }
    }

    /**
     * Clones this [HttpClientConfig] by duplicating all the [plugins] and [customInterceptors].
     */
    public fun clone(): HttpClientConfig<T> {
        val result = HttpClientConfig<T>()
        result += this
        return result
    }

    /**
     * Installs the plugin from the [other] client's configuration.
     */
    public operator fun plusAssign(other: HttpClientConfig<out T>) {
        followRedirects = other.followRedirects
        useDefaultTransformers = other.useDefaultTransformers
        expectSuccess = other.expectSuccess

        plugins += other.plugins
        pluginConfigurations += other.pluginConfigurations
        customInterceptors += other.customInterceptors
    }
}
