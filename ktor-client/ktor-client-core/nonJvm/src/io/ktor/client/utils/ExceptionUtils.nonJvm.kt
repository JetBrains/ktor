/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

import io.ktor.utils.io.*

/**
 * If the exception contains cause that differs from [CancellationException] returns it otherwise returns itself.
 */
public actual fun Throwable.unwrapCancellationException(): Throwable {
    var exception: Throwable? = this
    while (exception is CancellationException) {
        // If there is a cycle, we return the initial exception.
        if (exception == exception.cause) {
            return this
        }
        exception = exception.cause
    }

    return exception ?: this
}
