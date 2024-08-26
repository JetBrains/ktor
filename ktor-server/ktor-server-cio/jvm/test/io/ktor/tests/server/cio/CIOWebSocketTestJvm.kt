/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.junit.*
import io.ktor.server.cio.*
import io.ktor.server.test.base.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.*
import org.junit.jupiter.api.extension.*
import kotlin.test.*

@ExtendWith(RetrySupport::class)
class CIOWebSocketTestJvm : EngineTestBase<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {

    init {
        enableSsl = false
        enableHttp2 = false
    }

    @Ignore // TODO flaky
    @RetryableTest
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testNonWebsocketRouteDoNotLeakOnWsRequest() = runTest {
        createAndStartServer {}
        DebugProbes.install()
        runBlocking {
            HttpClient(io.ktor.client.engine.cio.CIO) {
                install(WebSockets)
            }.use { httpClient ->
                val requests = (1..200).map { id ->
                    launch {
                        assertFails { httpClient.webSocket("ws://127.0.0.1:$port/$id") {} }
                    }
                }
                requests.joinAll()
            }
        }
        val coroutinesCount = DebugProbes.dumpCoroutinesInfo().size
        assertTrue(10 > coroutinesCount)
    }
}
