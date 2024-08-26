// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlin.coroutines.*

internal actual class TestHttpClientEngineBridge actual constructor(
    engine: TestHttpClientEngine,
    app: TestApplicationEngine
) {

    actual val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    actual suspend fun runWebSocketRequest(
        url: String,
        headers: Headers,
        content: OutgoingContent,
        callContext: CoroutineContext
    ): Pair<TestApplicationCall, WebSocketSession> {
        throw NotImplementedError("Websockets for js/wasm are not supported")
    }
}
