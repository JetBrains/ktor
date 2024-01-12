/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class CommonHttpClientJvmTest {
    @Test
    fun testHttpClientWithCustomEngineLifecycle() {
        val engine = MockEngine { respondOk() }
        val client = HttpClient(engine)
        client.close()

        // When the engine is provided by a user it should not be closed together with the client.
        assertTrue { engine.isActive }
    }

    @Test
    fun testHttpClientWithFactoryEngineLifecycle() {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respondOk() }
            }
        }
        val engine = client.engine
        client.close()

        // When the engine is provided by Ktor factory it should be closed together with the client.
        assertFalse { engine.isActive }
    }

    @Test
    fun testHttpClientClosesInstalledFeatures() {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("") } }
            install(TestPlugin)
        }
        client.close()
        assertTrue(client.plugin(TestPlugin).closed)
    }

    class TestPlugin : Closeable {
        var closed = false
        override fun close() {
            closed = true
        }

        companion object : HttpClientPlugin<Unit, TestPlugin> {
            override val key: AttributeKey<TestPlugin> = AttributeKey("TestPlugin")
            override fun install(plugin: TestPlugin, scope: HttpClient) = Unit
            override fun prepare(block: Unit.() -> Unit): TestPlugin = TestPlugin()
        }
    }
}
