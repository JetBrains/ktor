/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Creates an [ApplicationPlugin] that can be installed into an [Application].
 *
 * The example below creates a plugin that prints a requested URL each time your application receives a call:
 * ```
 * val RequestLoggingPlugin = createApplicationPlugin("RequestLoggingPlugin") {
 *      onCall { call ->
 *          println(call.request.uri)
 *      }
 * }
 *
 * application.install(RequestLoggingPlugin)
 * ```
 *
 * You can learn more from [Custom plugins](https://ktor.io/docs/custom-plugins.html).
 *
 * @param name A name of a plugin that is used to get its instance.
 * @param configurationPath is path in configuration file to configuration of this plugin.
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created.
 * Note that it may be modified later when a user of your plugin calls [Application.install].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 **/
public fun <PluginConfigT : Any> createApplicationPlugin(
    name: String,
    configurationPath: String,
    createConfiguration: (config: ApplicationConfig) -> PluginConfigT,
    body: PluginBuilder<PluginConfigT>.() -> Unit
): ApplicationPlugin<PluginConfigT> = object : ApplicationPlugin<PluginConfigT> {
    override val key: AttributeKey<PluginInstance> = AttributeKey(name)

    override fun install(
        pipeline: Application,
        configure: PluginConfigT.() -> Unit
    ): PluginInstance {
        val config = try {
            pipeline.environment.config.config(configurationPath)
        } catch (_: Throwable) {
            MapApplicationConfig()
        }
        return createPluginInstance(pipeline, pipeline, body, { createConfiguration(config) }, configure)
    }
}

/**
 * Creates an [ApplicationPlugin] that can be installed into an [Application].
 *
 * The example below creates a plugin that prints a requested URL each time your application receives a call:
 * ```
 * val RequestLoggingPlugin = createApplicationPlugin("RequestLoggingPlugin") {
 *      onCall { call ->
 *          println(call.request.uri)
 *      }
 * }
 *
 * application.install(RequestLoggingPlugin)
 * ```
 *
 * You can learn more from [Custom plugins](https://ktor.io/docs/custom-plugins.html).
 *
 * @param name A name of a plugin that is used to get its instance.
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created.
 * Note that it may be modified later when a user of your plugin calls [Application.install].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 *
 **/
public fun <PluginConfigT : Any> createApplicationPlugin(
    name: String,
    createConfiguration: () -> PluginConfigT,
    body: PluginBuilder<PluginConfigT>.() -> Unit
): ApplicationPlugin<PluginConfigT> = object : ApplicationPlugin<PluginConfigT> {
    override val key: AttributeKey<PluginInstance> = AttributeKey(name)

    override fun install(
        pipeline: Application,
        configure: PluginConfigT.() -> Unit
    ): PluginInstance {
        return createPluginInstance(pipeline, pipeline, body, createConfiguration, configure)
    }
}

/**
 * Creates a [RouteScopedPlugin] that can be installed into a [io.ktor.server.routing.RouteNode].
 *
 * The example below creates a plugin that prints a requested URL each time your application receives a call:
 * ```
 * val RequestLoggingPlugin = createRouteScopedPlugin("RequestLoggingPlugin") {
 *      onCall { call ->
 *          println(call.request.uri)
 *      }
 * }
 *
 * route("index") {
 *   install(RequestLoggingPlugin)
 * }
 * ```
 *
 * You can learn more from [Custom plugins](https://ktor.io/docs/custom-plugins.html).
 *
 * @param name A name of a plugin that is used to get its instance
 * when it is installed to [io.ktor.server.routing.Routing].
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created. Please
 * note that it may be modified later when a user of your plugin calls [install].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 **/
public fun <PluginConfigT : Any> createRouteScopedPlugin(
    name: String,
    createConfiguration: () -> PluginConfigT,
    body: RouteScopedPluginBuilder<PluginConfigT>.() -> Unit
): RouteScopedPlugin<PluginConfigT> = object : RouteScopedPlugin<PluginConfigT> {

    override val key: AttributeKey<PluginInstance> = AttributeKey(name)

    override fun install(
        pipeline: ApplicationCallPipeline,
        configure: PluginConfigT.() -> Unit
    ): PluginInstance {
        val application = when (pipeline) {
            is RouteNode -> pipeline.application
            is Application -> pipeline
            else -> error("Unsupported pipeline type: ${pipeline::class}")
        }

        return createRouteScopedPluginInstance(application, pipeline, body, createConfiguration, configure)
    }
}

/**
 * Creates a [RouteScopedPlugin] that can be installed into a [io.ktor.server.routing.RouteNode].
 *
 * The example below creates a plugin that prints a requested URL each time your application receives a call:
 * ```
 * val RequestLoggingPlugin = createRouteScopedPlugin("RequestLoggingPlugin") {
 *      onCall { call ->
 *          println(call.request.uri)
 *      }
 * }
 *
 * route("index") {
 *   install(RequestLoggingPlugin)
 * }
 * ```
 *
 * You can learn more from [Custom plugins](https://ktor.io/docs/custom-plugins.html).
 *
 * @param name A name of a plugin that is used to get its instance
 * when it is installed to [io.ktor.server.routing.Routing].
 * @param configurationPath is path in configuration file to configuration of this plugin.
 * @param createConfiguration Defines how the initial [PluginConfigT] of your new plugin can be created. Please
 * note that it may be modified later when a user of your plugin calls [install].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 **/
public fun <PluginConfigT : Any> createRouteScopedPlugin(
    name: String,
    configurationPath: String,
    createConfiguration: (config: ApplicationConfig) -> PluginConfigT,
    body: RouteScopedPluginBuilder<PluginConfigT>.() -> Unit
): RouteScopedPlugin<PluginConfigT> = object : RouteScopedPlugin<PluginConfigT> {

    override val key: AttributeKey<PluginInstance> = AttributeKey(name)

    override fun install(
        pipeline: ApplicationCallPipeline,
        configure: PluginConfigT.() -> Unit
    ): PluginInstance {
        val environment = pipeline.environment

        val config = try {
            environment.config.config(configurationPath)
        } catch (_: Throwable) {
            MapApplicationConfig()
        }

        val application = when (pipeline) {
            is RouteNode -> pipeline.application
            is Application -> pipeline
            else -> error("Unsupported pipeline type: ${pipeline::class}")
        }

        return createRouteScopedPluginInstance(application, pipeline, body, { createConfiguration(config) }, configure)
    }
}

/**
 * Creates an [ApplicationPlugin] that can be installed into an [Application].
 *
 * The example below creates a plugin that prints a requested URL each time your application receives a call:
 * ```
 * val RequestLoggingPlugin = createApplicationPlugin("RequestLoggingPlugin") {
 *      onCall { call ->
 *          println(call.request.uri)
 *      }
 * }
 *
 * application.install(RequestLoggingPlugin)
 * ```
 *
 * You can learn more from [Custom plugins](https://ktor.io/docs/custom-plugins.html).
 *
 * @param name A name of a plugin that is used to get an instance of the plugin installed to the [Application].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 **/
public fun createApplicationPlugin(
    name: String,
    body: PluginBuilder<Unit>.() -> Unit
): ApplicationPlugin<Unit> = createApplicationPlugin(name, {}, body)

/**
 * Creates a [RouteScopedPlugin] that can be installed into a [io.ktor.server.routing.RouteNode].
 *
 * The example below creates a plugin that prints a requested URL each time your application receives a call:
 * ```
 * val RequestLoggingPlugin = createRouteScopedPlugin("RequestLoggingPlugin") {
 *      onCall { call ->
 *          println(call.request.uri)
 *      }
 * }
 *
 * route("index") {
 *   install(RequestLoggingPlugin)
 * }
 * ```
 *
 * You can learn more from [Custom plugins](https://ktor.io/docs/custom-plugins.html).
 *
 * @param name A name of a plugin that is used to get an instance of the plugin installed to the [io.ktor.server.routing.RouteNode].
 * @param body Allows you to define handlers ([onCall], [onCallReceive], [onCallRespond] and so on) that
 * can modify the behaviour of an [Application] where your plugin is installed.
 **/
public fun createRouteScopedPlugin(
    name: String,
    body: RouteScopedPluginBuilder<Unit>.() -> Unit
): RouteScopedPlugin<Unit> = createRouteScopedPlugin(name, {}, body)

private fun <
    PipelineT : ApplicationCallPipeline,
    PluginConfigT : Any
    > Plugin<PipelineT, PluginConfigT, PluginInstance>.createPluginInstance(
    application: Application,
    pipeline: ApplicationCallPipeline,
    body: PluginBuilder<PluginConfigT>.() -> Unit,
    createConfiguration: () -> PluginConfigT,
    configure: PluginConfigT.() -> Unit
): PluginInstance {
    val config = createConfiguration().apply(configure)

    val currentPlugin = this
    val pluginBuilder = object : PluginBuilder<PluginConfigT>(currentPlugin.key) {
        override val application: Application = application
        override val pipeline: ApplicationCallPipeline = pipeline
        override val pluginConfig: PluginConfigT = config
    }

    pluginBuilder.setupPlugin(body)
    return PluginInstance(pluginBuilder)
}

private fun <
    PipelineT : ApplicationCallPipeline,
    PluginConfigT : Any
    > Plugin<PipelineT, PluginConfigT, PluginInstance>.createRouteScopedPluginInstance(
    application: Application,
    pipeline: ApplicationCallPipeline,
    body: RouteScopedPluginBuilder<PluginConfigT>.() -> Unit,
    createConfiguration: () -> PluginConfigT,
    configure: PluginConfigT.() -> Unit
): PluginInstance {
    val config = createConfiguration().apply(configure)

    val currentPlugin = this
    val pluginBuilder = object : RouteScopedPluginBuilder<PluginConfigT>(currentPlugin.key) {
        override val application: Application = application
        override val pipeline: ApplicationCallPipeline = pipeline
        override val pluginConfig: PluginConfigT = config
        override val route: RouteNode? = pipeline as? RouteNode
    }

    pluginBuilder.setupPlugin(body)
    return PluginInstance(pluginBuilder)
}

private fun <Configuration : Any, Builder : PluginBuilder<Configuration>> Builder.setupPlugin(
    body: Builder.() -> Unit
) {
    apply(body)

    callInterceptions.forEach {
        it.action(pipeline)
    }

    onReceiveInterceptions.forEach {
        it.action(pipeline.receivePipeline)
    }

    onResponseInterceptions.forEach {
        it.action(pipeline.sendPipeline)
    }

    afterResponseInterceptions.forEach {
        it.action(pipeline.sendPipeline)
    }

    hooks.forEach { it.install(pipeline) }
}
