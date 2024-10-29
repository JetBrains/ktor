/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import java.lang.management.*

internal actual val DEBUG: Boolean by lazy {
    try {
        ManagementFactory.getRuntimeMXBean()
            .inputArguments.toString()
            .contains("jdwp")
    } catch (error: Throwable) {
        false
    }
}
