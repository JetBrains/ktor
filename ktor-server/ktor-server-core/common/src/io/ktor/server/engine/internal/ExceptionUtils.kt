/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import io.ktor.utils.io.errors.*
import kotlinx.io.IOException

public expect open class ClosedChannelException : IOException

internal expect open class OutOfMemoryError : Error

internal expect open class TimeoutException : Exception
