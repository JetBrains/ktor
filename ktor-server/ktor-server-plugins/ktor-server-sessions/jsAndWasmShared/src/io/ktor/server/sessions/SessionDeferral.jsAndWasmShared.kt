/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.application.ApplicationCall

internal actual fun isDeferredSessionsEnabled(): Boolean = false

internal actual fun createDeferredSession(call: ApplicationCall, providers: List<SessionProvider<*>>): StatefulSession =
    TODO("Deferred session retrieval is currently only available for JVM")
