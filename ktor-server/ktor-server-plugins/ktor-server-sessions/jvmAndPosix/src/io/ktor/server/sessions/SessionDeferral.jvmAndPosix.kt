/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

internal actual fun createDeferredSession(call: ApplicationCall, providers: List<SessionProvider<*>>): StatefulSession =
    BlockingDeferredSessionData(
        call.coroutineContext,
        providers.associateBy({ it.name }) {
            CoroutineScope(call.coroutineContext).async(start = CoroutineStart.LAZY) {
                it.receiveSessionData(call)
            }
        }
    )
