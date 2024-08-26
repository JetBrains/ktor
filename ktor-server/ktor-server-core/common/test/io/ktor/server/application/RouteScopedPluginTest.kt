/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlin.test.*

class RouteScopedPluginTest {

    @Test
    fun testPluginInstalledTopLevel() = testApplication {
        application {
            install(TestPlugin)
            routing {
                install(TestPlugin)
            }
        }
        assertFailsWith<DuplicatePluginException> {
            startApplication()
        }
    }

    @Test
    fun testPluginInstalledInRoutingScope() = testApplication {
        val callbackResults = mutableListOf<String>()

        routing {
            route("root-no-plugin") {
                route("first-plugin") {
                    install(TestPlugin) {
                        name = "foo"
                        desc = "test plugin"
                        pipelineCallback = { callbackResults.add(it) }
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }

                    route("inner") {
                        route("new-plugin") {
                            install(TestPlugin) {
                                name = "bar"
                                pipelineCallback = { callbackResults.add(it) }
                            }

                            route("inner") {
                                handle {
                                    call.respond(call.receive<String>())
                                }
                            }

                            handle {
                                call.respond(call.receive<String>())
                            }
                        }

                        handle {
                            call.respond(call.receive<String>())
                        }
                    }
                }

                handle {
                    call.respond(call.receive<String>())
                }
            }
        }

        on("making get request to /root-no-plugin") {
            val result = client.post("/root-no-plugin") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should not be invoked") {
                assertEquals(0, callbackResults.size)
            }
        }

        on("making get request to /root-no-plugin/first-plugin") {
            val result = client.post("/root-no-plugin/first-plugin") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("foo test plugin", callbackResults[0])
                callbackResults.clear()
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner") {
            val result = client.post("/root-no-plugin/first-plugin/inner") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("foo test plugin", callbackResults[0])
                callbackResults.clear()
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner/new-plugin") {
            val result = client.post("/root-no-plugin/first-plugin/inner/new-plugin") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("bar defaultDesc", callbackResults[0])
                callbackResults.clear()
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner/new-plugin/inner") {
            val result = client.post("/root-no-plugin/first-plugin/inner/new-plugin/inner") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("bar defaultDesc", callbackResults[0])
                callbackResults.clear()
            }
        }
    }

    @Test
    fun testPluginDoNotReuseConfig() = testApplication {
        val callbackResults = mutableListOf<String>()

        routing {
            route("root") {
                install(TestPlugin) {
                    name = "foo"
                    desc = "test plugin"
                    pipelineCallback = { callbackResults.add(it) }
                }
                route("plugin1") {
                    install(TestPlugin) {
                        desc = "new desc"
                        pipelineCallback = { callbackResults.add(it) }
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }

                    route("plugin2") {
                        install(TestPlugin) {
                            name = "new name"
                            pipelineCallback = { callbackResults.add(it) }
                        }

                        handle {
                            call.respond(call.receive<String>())
                        }
                    }
                }

                handle {
                    call.respond(call.receive<String>())
                }
            }
        }

        on("making get request to /root") {
            val result = client.post("/root") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("foo test plugin", callbackResults[0])
                callbackResults.clear()
            }
        }

        on("making get request to /root/plugin1") {
            val result = client.post("/root/plugin1") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("defaultName new desc", callbackResults[0])
                callbackResults.clear()
            }
        }

        on("making get request to /root/plugin1/plugin2") {
            val result = client.post("/root/plugin1/plugin2") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("new name defaultDesc", callbackResults[0])
                callbackResults.clear()
            }
        }
    }

    @Test
    fun testMultiplePluginInstalledAtTheSameRoute() = testApplication {
        routing {
            route("root") {
                install(TestPlugin)
            }
            route("root") {
                install(TestPlugin)
            }
        }
        assertFailsWith<DuplicatePluginException> {
            startApplication()
        }
    }

    @Test
    fun testAllPipelinesPlugin() = testApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        routing {
            route("root-no-plugin") {
                route("first-plugin") {
                    install(TestAllPipelinesPlugin) {
                        name = "foo"
                        desc = "test plugin"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }

                    route("inner") {
                        route("new-plugin") {
                            install(TestAllPipelinesPlugin) {
                                name = "bar"
                                addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                            }

                            route("inner") {
                                handle {
                                    call.respond(call.receive<String>())
                                }
                            }

                            handle {
                                call.respond(call.receive<String>())
                            }
                        }

                        handle {
                            call.respond(call.receive<String>())
                        }
                    }
                }

                handle {
                    call.respond(call.receive<String>())
                }
            }
        }

        on("making get request to /root-no-plugin") {
            val result = client.post("/root-no-plugin") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should not be invoked") {
                allCallbacks.forEach {
                    assertEquals(0, it.size)
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin") {
            val result = client.post("/root-no-plugin/first-plugin") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test plugin", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner") {
            val result = client.post("/root-no-plugin/first-plugin/inner") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test plugin", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner/new-plugin") {
            val result = client.post("/root-no-plugin/first-plugin/inner/new-plugin") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar defaultDesc", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner/new-plugin/inner") {
            val result = client.post("/root-no-plugin/first-plugin/inner/new-plugin/inner") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar defaultDesc", it[0])
                    it.clear()
                }
            }
        }
    }

    @Test
    fun testCustomPhase() = testApplication {
        val callbackResults = mutableListOf<String>()

        routing {
            route("root") {
                install(TestPluginCustomPhase) {
                    name = "foo"
                    desc = "first plugin"
                    callback = { callbackResults.add(it) }
                }

                handle {
                    call.respond(call.receive<String>())
                }
                route("a") {
                    install(TestPluginCustomPhase) {
                        name = "bar"
                        desc = "second plugin"
                        callback = { callbackResults.add(it) }
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }
                }
            }
        }

        on("making get request to /root") {
            val result = client.get("/root") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("second callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("foo first plugin", callbackResults[0])
                callbackResults.clear()
            }
        }

        on("making get request to /root/a") {
            val result = client.get("/root/a") {
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.status)
            }
            it("second callback should be invoked") {
                assertEquals(1, callbackResults.size)
                assertEquals("bar second plugin", callbackResults[0])
                callbackResults.clear()
            }
        }
    }

    private fun TestAllPipelinesPlugin.Config.addCallbacks(
        callbackResults: MutableList<String>,
        receiveCallbackResults: MutableList<String>,
        sendCallbackResults: MutableList<String>
    ) {
        pipelineCallback = { callbackResults.add(it) }
        receivePipelineCallback = { receiveCallbackResults.add(it) }
        sendPipelineCallback = { sendCallbackResults.add(it) }
    }
}

class TestAllPipelinesPlugin private constructor(config: Config) {

    private val pipelineCallback = config.pipelineCallback
    private val receivePipelineCallback = config.receivePipelineCallback
    private val sendPipelineCallback = config.sendPipelineCallback
    private val name = config.name
    private val desc = config.desc

    fun install(pipeline: ApplicationCallPipeline) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            pipelineCallback("$name $desc")
        }
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) {
            receivePipelineCallback("$name $desc")
        }
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
            sendPipelineCallback("$name $desc")
        }
    }

    @KtorDsl
    class Config(
        var name: String = "defaultName",
        var desc: String = "defaultDesc",
        var pipelineCallback: (String) -> Unit = {},
        var receivePipelineCallback: (String) -> Unit = {},
        var sendPipelineCallback: (String) -> Unit = {},
    )

    companion object Plugin : BaseRouteScopedPlugin<Config, TestAllPipelinesPlugin> {

        override val key: AttributeKey<TestAllPipelinesPlugin> = AttributeKey("TestPlugin")

        override fun install(pipeline: ApplicationCallPipeline, configure: Config.() -> Unit): TestAllPipelinesPlugin {
            val config = Config().apply(configure)
            val plugin = TestAllPipelinesPlugin(config)
            return plugin.apply { install(pipeline) }
        }
    }
}

class TestPlugin private constructor(config: Config) {
    private val pipelineCallback = config.pipelineCallback
    private val name = config.name
    private val desc = config.desc

    fun install(pipeline: ApplicationCallPipeline) {
        pipeline.intercept(ApplicationCallPipeline.Fallback) {
            pipelineCallback("$name $desc")
        }
    }

    @KtorDsl
    class Config(
        var name: String = "defaultName",
        var desc: String = "defaultDesc",
        var pipelineCallback: (String) -> Unit = {}
    )

    companion object Plugin : BaseRouteScopedPlugin<Config, TestPlugin> {

        override val key: AttributeKey<TestPlugin> = AttributeKey("TestPlugin")

        override fun install(pipeline: ApplicationCallPipeline, configure: Config.() -> Unit): TestPlugin {
            val config = Config().apply(configure)
            val plugin = TestPlugin(config)
            return plugin.apply { install(pipeline) }
        }
    }
}

class TestPluginCustomPhase private constructor(config: Config) {
    private val callback = config.callback
    private val name = config.name
    private val desc = config.desc

    fun install(pipeline: ApplicationCallPipeline) {
        val phase = PipelinePhase("new phase")
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, phase)
        pipeline.intercept(phase) {
            callback("$name $desc")
        }
    }

    @KtorDsl
    class Config(
        var name: String = "defaultName",
        var desc: String = "defaultDesc",
        var callback: (String) -> Unit = {},
    )

    companion object Plugin : BaseRouteScopedPlugin<Config, TestPluginCustomPhase> {
        override val key: AttributeKey<TestPluginCustomPhase> = AttributeKey("TestPlugin")

        override fun install(pipeline: ApplicationCallPipeline, configure: Config.() -> Unit): TestPluginCustomPhase {
            val config = Config().apply(configure)
            val plugin = TestPluginCustomPhase(config)
            return plugin.apply { install(pipeline) }
        }
    }
}
