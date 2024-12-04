/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.debug

import io.ktor.util.debug.plugins.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Initialize plugins debug mode for [block]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.debug.initContextInDebugMode)
 */
public suspend fun <T> initContextInDebugMode(
    block: suspend () -> T
): T {
    if (!IntellijIdeaDebugDetector.isDebuggerConnected) return block()

    val debugContext = currentCoroutineContext() + PluginsTrace()
    return withContext(debugContext) { block() }
}

/**
 * Adds [pluginName] to the current [CoroutineContext] if Intellij JVM debugger is attached.
 */
public suspend fun <T> addToContextInDebugMode(
    pluginName: String,
    block: suspend () -> T
): T {
    if (!IntellijIdeaDebugDetector.isDebuggerConnected) return block()

    val debugContext = currentCoroutineContext() + PluginName(pluginName)
    return withContext(debugContext) { block() }
}

/**
 * Performs [action] on the current element of the [CoroutineContext] with the given [key] if Intellij JVM debugger is
 * attached.
 * */
public suspend fun <Element : CoroutineContext.Element> useContextElementInDebugMode(
    key: CoroutineContext.Key<Element>,
    action: (Element) -> Unit
) {
    if (!IntellijIdeaDebugDetector.isDebuggerConnected) return

    currentCoroutineContext()[key]?.let(action)
}
