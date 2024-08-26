/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.internal

import kotlinx.coroutines.*

internal expect val Dispatchers.IOBridge: CoroutineDispatcher

// this bridge is necessary, as there is no `runBlocking` for Js/Wasm
//  it's only used in OLD test API via `withTestApplication`
//  new `testApplication` API works fine
internal expect fun <T> maybeRunBlocking(block: suspend CoroutineScope.() -> T): T
