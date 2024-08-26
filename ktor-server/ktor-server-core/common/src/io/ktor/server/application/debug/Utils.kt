/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.debug

import io.ktor.util.debug.*
import io.ktor.util.debug.plugins.*

internal suspend fun ijDebugReportHandlerStarted(pluginName: String, handler: String) {
    useContextElementInDebugMode(PluginsTrace) { trace ->
        trace.eventOrder.add(PluginTraceElement(pluginName, handler, PluginTraceElement.PluginEvent.STARTED))
    }
}

internal suspend fun ijDebugReportHandlerFinished(pluginName: String, handler: String) {
    useContextElementInDebugMode(PluginsTrace) { trace ->
        trace.eventOrder.add(PluginTraceElement(pluginName, handler, PluginTraceElement.PluginEvent.FINISHED))
    }
}
