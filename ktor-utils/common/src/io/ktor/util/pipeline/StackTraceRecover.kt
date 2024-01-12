/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import kotlinx.coroutines.internal.*
import kotlin.coroutines.*

/**
 * Recreates the exception with the original cause to keep exception structure.
 *
 * Notice: This method breaks the [exception] identity.
 */
internal fun recoverStackTraceBridge(exception: Throwable, continuation: Continuation<*>): Throwable = try {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    recoverStackTrace(exception, continuation).withCause(exception.cause)
} catch (_: Throwable) {
    exception
}

internal expect fun Throwable.withCause(cause: Throwable?): Throwable
