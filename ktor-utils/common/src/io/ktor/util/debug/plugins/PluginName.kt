/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.debug.plugins

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Name of the Ktor plugin that is currently being invoked. This name is used in debugging mode.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.debug.plugins.PluginName)
 */
public data class PluginName(
    /**
     * Plugin name.
     */
    val pluginName: String
) : AbstractCoroutineContextElement(PluginName) {
    /**
     * Key for [PluginName] instance in the coroutine context.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.debug.plugins.PluginName.Key)
     */
    public companion object Key : CoroutineContext.Key<PluginName>

    /**
     * Returns a string representation of the object.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.debug.plugins.PluginName.toString)
     */
    override fun toString(): String = "PluginName($pluginName)"
}
