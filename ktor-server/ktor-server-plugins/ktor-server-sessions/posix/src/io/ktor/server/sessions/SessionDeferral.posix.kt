/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun isDeferredSessionsEnabled(): Boolean =
    getenv(SESSIONS_DEFERRED_FLAG)?.toKString()?.toBoolean() == true
