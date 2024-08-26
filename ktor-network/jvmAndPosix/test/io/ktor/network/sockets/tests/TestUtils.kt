/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

internal fun testSockets(timeout: Duration = 1.seconds, block: suspend CoroutineScope.(SelectorManager) -> Unit) {
    if (!PlatformUtils.IS_JVM && !PlatformUtils.IS_NATIVE) return
    testSuspend {
        withTimeout(timeout) {
            SelectorManager().use { selector ->
                block(selector)
            }
        }
    }
}

internal expect fun Any.supportsUnixDomainSockets(): Boolean

internal expect fun createTempFilePath(basename: String): String
internal expect fun removeFile(path: String)
