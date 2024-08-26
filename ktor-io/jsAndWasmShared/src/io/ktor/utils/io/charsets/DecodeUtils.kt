/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

internal inline fun <R> decodeWrap(block: () -> R): R {
    try {
        return block()
    } catch (cause: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${cause.message ?: "no cause provided"}")
    }
}
