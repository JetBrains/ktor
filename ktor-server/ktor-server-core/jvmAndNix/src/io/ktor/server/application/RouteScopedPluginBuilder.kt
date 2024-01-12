/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.server.routing.*
import io.ktor.util.*

/**
 * Utility class to build a [RouteScopedPlugin] instance.
 **/
public abstract class RouteScopedPluginBuilder<PluginConfig : Any>(key: AttributeKey<PluginInstance>) :
    PluginBuilder<PluginConfig>(key) {

    /**
     * A [RouteNode] to which this plugin was installed. Can be `null` if plugin in installed into [Application].
     **/
    public abstract val route: RouteNode?
}
