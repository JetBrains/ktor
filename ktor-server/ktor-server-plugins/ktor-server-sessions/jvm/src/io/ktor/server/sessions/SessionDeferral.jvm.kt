/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

internal actual fun isDeferredSessionsEnabled(): Boolean =
    System.getProperty(SESSIONS_DEFERRED_FLAG)?.toBoolean() == true
