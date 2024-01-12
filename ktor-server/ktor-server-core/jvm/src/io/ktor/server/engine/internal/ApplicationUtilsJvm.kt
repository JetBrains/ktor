/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*

internal actual fun availableProcessorsBridge(): Int = Runtime.getRuntime().availableProcessors()

internal actual val Dispatchers.IOBridge: CoroutineDispatcher get() = IO

internal actual fun printError(message: Any?) {
    System.err.print(message)
}

internal actual fun configureShutdownUrl(config: ApplicationConfig, pipeline: EnginePipeline) {
    val url = config.propertyOrNull("ktor.deployment.shutdown.url")?.getString() ?: return
    pipeline.install(ShutDownUrl.EnginePlugin) {
        shutDownUrl = url
    }
}
