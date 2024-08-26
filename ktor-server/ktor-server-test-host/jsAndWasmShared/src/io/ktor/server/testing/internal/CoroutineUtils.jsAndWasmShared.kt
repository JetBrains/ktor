/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.internal

import kotlinx.coroutines.*

internal actual val Dispatchers.IOBridge: CoroutineDispatcher get() = Default

internal actual fun <T> maybeRunBlocking(block: suspend CoroutineScope.() -> T): T =
    error("run blocking is not supported on JS/Wasm")
