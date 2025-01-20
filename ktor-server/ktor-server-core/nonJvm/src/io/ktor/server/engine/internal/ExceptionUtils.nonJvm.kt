/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import kotlinx.io.IOException

public actual open class ClosedChannelException(message: String) : IOException(message)

internal actual open class OutOfMemoryError : Error()

internal actual open class TimeoutException : Exception()
