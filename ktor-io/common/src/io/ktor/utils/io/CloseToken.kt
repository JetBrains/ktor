/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.io.IOException

internal val CLOSED = CloseToken(null)

@Suppress("OPT_IN_USAGE")
internal class CloseToken(origin: Throwable?) {

    private val closedException: Throwable? = when {
        origin == null -> null
        origin is CancellationException -> {
            if (origin is CopyableThrowable<*>) {
                origin.createCopy()
            } else {
                CancellationException(origin.message ?: "Channel was cancelled", origin)
            }
        }

        origin is IOException && origin is CopyableThrowable<*> -> origin.createCopy()
        else -> IOException(origin.message ?: "Channel was closed", origin)
    }

    val cause: Throwable?
        get() = when {
            closedException == null -> null
            (closedException is IOException) -> {
                if (closedException is CopyableThrowable<*>) {
                    closedException.createCopy()
                } else {
                    IOException(closedException.message, closedException)
                }
            }

            closedException is CopyableThrowable<*> ->
                closedException.createCopy() ?: CancellationException(closedException.message, closedException)

            else -> CancellationException(closedException.message, closedException)
        }
}
