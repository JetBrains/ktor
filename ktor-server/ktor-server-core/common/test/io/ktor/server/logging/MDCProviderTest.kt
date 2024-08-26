/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.logging

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import kotlin.coroutines.*
import kotlin.test.*

class MDCProviderTest {

    @Test
    fun testLogErrorWithEmptyApplication() = testApplication {
        val monitor = Events()
        val environment = createTestEnvironment { }
        val engine = object : ApplicationEngine {
            override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = TODO("Not yet implemented")
            override val environment: ApplicationEnvironment get() = TODO("Not yet implemented")
            override fun start(wait: Boolean): ApplicationEngine = TODO("Not yet implemented")
            override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) = TODO("Not yet implemented")
        }
        val application = Application(environment, false, "/", monitor, EmptyCoroutineContext) { engine }
        assertNotNull(application.mdcProvider)
    }
}
