/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.util.logging.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Handles all uncaught exceptions and logs errors with the specified [logger]
 * ignoring [CancellationException] and [IOException].
 */
public class DefaultUncaughtExceptionHandler(
    private val logger: () -> Logger
) : CoroutineExceptionHandler {
    public constructor(logger: Logger) : this({ logger })

    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler.Key

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        if (exception is CancellationException) return
        if (exception is IOException) return

        val coroutineName = context[CoroutineName] ?: context.toString()

        logger().error("Unhandled exception caught for $coroutineName", exception)
    }
}
