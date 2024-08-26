/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

/**
 * An exception that is thrown when the current body in the HTTP pipeline is invalid.
 */
public class InvalidBodyException(message: String) : Exception(message)

internal fun noBinaryDataException(
    expectedTypeName: String,
    subject: Any?
): InvalidBodyException {
    val message = "Expected $expectedTypeName type but ${subject?.let { it::class.simpleName } ?: "null"} found"
    return InvalidBodyException(message)
}
