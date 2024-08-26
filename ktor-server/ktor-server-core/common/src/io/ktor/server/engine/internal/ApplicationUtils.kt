/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import io.ktor.server.config.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*

internal expect fun availableProcessorsBridge(): Int

internal expect val Dispatchers.IOBridge: CoroutineDispatcher

internal expect fun printError(message: Any?)

internal expect fun configureShutdownUrl(config: ApplicationConfig, pipeline: EnginePipeline)
