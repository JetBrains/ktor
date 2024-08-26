/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.client.request.*
import io.ktor.server.application.hooks.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class HooksTest {

    @Test
    fun testCustomHookExecuted() = runTest {
        class HookHandler {
            var called: Boolean = false
            var executed: Boolean = false
        }

        val currentHandler = HookHandler()

        val myHook = object : Hook<HookHandler.() -> Unit> {
            override fun install(pipeline: ApplicationCallPipeline, handler: HookHandler.() -> Unit) {
                currentHandler.apply(handler)

                pipeline.intercept(ApplicationCallPipeline.Call) {
                    currentHandler.executed = true
                }
            }
        }

        val testPlugin = createApplicationPlugin("TestPlugin") {
            on(myHook) {
                called = true
            }
        }

        assertFalse(currentHandler.called)

        runTestApplication {
            install(testPlugin)

            routing {
                handle {
                    call.respondText("OK")
                }
            }

            assertFalse(currentHandler.executed)
            client.get("/")

            assertTrue(currentHandler.executed)
        }

        assertTrue(currentHandler.called)
    }

    @Test
    fun testMonitoringEventHook() = runTest {
        class State {
            var startCalled = false
            var shutdownCalled = false
        }

        val state = State()
        val shutdownHandler = createApplicationPlugin("ShutdownHandler") {
            on(MonitoringEvent(ApplicationStopped)) {
                state.shutdownCalled = true
            }
            on(MonitoringEvent(ApplicationStarted)) {
                state.startCalled = true
            }
        }

        runTestApplication {
            install(shutdownHandler)

            routing {
                handle {
                    call.respondText("OK")
                }
            }

            client.get("/")
            assertTrue(state.startCalled)
            assertFalse(state.shutdownCalled)
        }

        assertTrue(state.shutdownCalled)
    }

    @Test
    fun testOnCallFailedHook() = testApplication {
        class State {
            var fail: Throwable? = null
        }

        val state = State()

        val failedHandler = createApplicationPlugin("FailedHandler") {
            on(CallFailed) { _, cause ->
                state.fail = cause
            }
        }

        install(failedHandler)

        routing {
            handle {
                error("Failure")
            }
        }

        assertNull(state.fail)

        runCatching {
            client.get("/")
        }

        assertNotNull(state.fail)
        assertEquals("Failure", state.fail?.message)
    }

    @Test
    fun testHookWithRoutingPlugin() = testApplication {
        val OnCallHook = object : Hook<() -> Unit> {
            override fun install(pipeline: ApplicationCallPipeline, handler: () -> Unit) {
                pipeline.intercept(ApplicationCallPipeline.Call) {
                    handler()
                }
            }
        }

        val myOutput = mutableListOf<Int>()
        fun myPrintln(value: Int) {
            myOutput += value
        }

        class MyConfig {
            var myValue: Int = 0
        }

        val MyRoutePlugin = createRouteScopedPlugin("MyRoutePlugin", ::MyConfig) {
            on(OnCallHook) {
                myPrintln(pluginConfig.myValue)
            }
        }

        routing {
            route("/1") {
                install(MyRoutePlugin) {
                    myValue = 1
                }
                get {
                    call.respond("Hello-1")
                }
            }
            route("/2") {
                install(MyRoutePlugin) {
                    myValue = 2
                }
                get {
                    call.respond("Hello-2")
                }
            }
            get("/3") {
                call.respond("Hello-3")
            }
        }

        suspend fun ApplicationTestBuilder.assertOutput(requestPath: String, expectedOutput: List<Int>) {
            myOutput.clear()
            runCatching {
                client.get(requestPath)
            }

            assertContentEquals(expectedOutput.toTypedArray(), myOutput.toTypedArray())
        }

        assertOutput("/1", listOf(1))
        assertOutput("/2", listOf(2))
        assertOutput("/3", emptyList())
    }
}
