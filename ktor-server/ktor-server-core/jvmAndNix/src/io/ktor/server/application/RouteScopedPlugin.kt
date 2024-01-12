/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.routing.*

/**
 * Defines a [Plugin](https://ktor.io/docs/plugins.html) that can be installed into a [RouteNode].
 * @param TConfiguration is the configuration object type for this Plugin
 * @param TPlugin is the instance type of the Plugin object
 */
public interface BaseRouteScopedPlugin<TConfiguration : Any, TPlugin : Any> :
    Plugin<ApplicationCallPipeline, TConfiguration, TPlugin>

/**
 * Defines a Plugin that can be installed into [RouteNode]
 * @param TConfiguration is the configuration object type for this Plugin
 */
public interface RouteScopedPlugin<TConfiguration : Any> : BaseRouteScopedPlugin<TConfiguration, PluginInstance>

/**
 * Finds the plugin [F] in the current [RouteNode]. If not found, search in the parent [RouteNode].
 *
 * @return [F] instance or `null` if not found
 */
public fun <F : Any> RouteNode.findPluginInRoute(plugin: Plugin<*, *, F>): F? {
    var current = this
    while (true) {
        val installedFeature = current.pluginOrNull(plugin)
        if (installedFeature != null) {
            return installedFeature
        }
        if (current.parent == null) {
            break
        }
        current = current.parent!!
    }
    if (current is Routing) {
        return application.pluginOrNull(plugin)
    }
    return null
}
