/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.internal.legacy.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import platform.Foundation.*

@OptIn(InternalAPI::class)
internal class DarwinLegacyClientEngine(
    override val config: DarwinLegacyClientEngineConfig
) : HttpClientEngineBase("ktor-darwin-legacy") {

    private val requestQueue: NSOperationQueue? = when (val queue = NSOperationQueue.currentQueue()) {
        NSOperationQueue.mainQueue -> NSOperationQueue()
        else -> queue
    }

    override val dispatcher = Dispatchers.Unconfined

    override val supportedCapabilities = setOf(HttpTimeoutCapability, SSECapability)

    private val session = DarwinLegacySession(config, requestQueue)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        return session.execute(data, callContext)
    }
}
