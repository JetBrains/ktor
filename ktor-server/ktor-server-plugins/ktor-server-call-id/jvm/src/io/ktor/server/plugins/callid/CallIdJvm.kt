/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.callid

import io.ktor.server.plugins.calllogging.*

/**
 * Put call id into MDC (diagnostic context value) with [name]
 */
public fun CallLoggingConfig.callIdMdc(name: String = "CallId") {
    mdc(name) { it.callId }
}
