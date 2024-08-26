/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ApplicationPluginTest {
    @Test
    fun test_empty_plugin_does_not_break_pipeline() = testApplication {
        val plugin = createApplicationPlugin("F", createConfiguration = {}) {
        }

        install(plugin)

        routing {
            get("/request") {
                call.respondText("response")
            }
        }

        assertEquals("response", client.get("/request").bodyAsText())
    }

    private data class SimplePluginConfig(var enabled: Boolean = true)

    private val simplePlugin = createApplicationPlugin("F", createConfiguration = { SimplePluginConfig() }) {
        onCall { call ->
            if (this@createApplicationPlugin.pluginConfig.enabled) {
                call.respondText("Plugin enabled!")
                finish()
            }
        }
    }

    @Test
    fun test_plugin_with_single_interception() = testApplication {
        install(simplePlugin)
        routing {
            get("/request") {
                call.respondText("response")
            }
        }

        assertEquals("Plugin enabled!", client.get("/request").bodyAsText())
    }

    @Test
    fun test_plugin_with_single_interception_disabled() = testApplication {
        install(simplePlugin) {
            enabled = false
        }
        routing {
            get("/request") {
                call.respondText("response")
            }
        }

        assertEquals("response", client.get("/request").bodyAsText())
    }

    private val multiPhasePlugin = createApplicationPlugin("F", createConfiguration = { }) {
        val key = AttributeKey<String>("FKey")

        onCall { call ->
            val data = call.request.headers["F"]
            if (data != null) {
                call.attributes.put(key, data)
            }
        }
        onCallRespond { call, _ ->
            val data = call.attributes.getOrNull(key)
            if (data != null) {
                transformBody { data }
            }
        }
    }

    @Test
    fun test_plugin_with_multiple_phases() = testApplication {
        install(multiPhasePlugin)

        routing {
            get("/request") {
                call.respondText("response")
            }
        }

        assertEquals("response", client.get("/request").bodyAsText())
    }

    @Test
    fun test_plugin_with_multiple_phases_custom_data(): TestResult = testApplication {
        install(multiPhasePlugin)
        routing {
            get("/request") {
                call.respondText("response")
            }
        }
        assertEquals(
            "custom data",
            client.get("/request") {
                header("F", "custom data")
            }.bodyAsText()
        )
    }

    class FConfig {
        companion object {
            val Key = AttributeKey<String>("FKey")
        }
    }

    class ConfigWithData {
        var data = ""
    }

    @Test
    fun test_multiple_installs_changing_config() = runTest {
        val pluginF = createApplicationPlugin("F", { ConfigWithData() }) {
            onCall { call ->
                val oldValue = pluginConfig.data
                pluginConfig.data = "newValue"
                val newValue = pluginConfig.data

                call.respondText("$oldValue:$newValue")
                finish()
            }
        }

        suspend fun assertWithPlugin(expectedResponse: String) = runTestApplication {
            install(pluginF) {
                data = "oldValue"
            }

            routing {
                get("/request") {
                    call.respondText("response")
                }
            }

            assertEquals(expectedResponse, client.get("/request").bodyAsText())
        }

        assertWithPlugin(expectedResponse = "oldValue:newValue")
        assertWithPlugin(expectedResponse = "oldValue:newValue")
        assertWithPlugin(expectedResponse = "oldValue:newValue")
    }

    var onCallProcessedTimes = 0

    @Test
    fun test_same_phase_defined_twice() = testApplication {
        val plugin = createApplicationPlugin("F") {
            onCall {
                onCallProcessedTimes += 1
            }

            onCall {
                onCallProcessedTimes += 1
            }
        }

        install(plugin)
        routing {
            get("/request") {
                call.respondText("response")
            }
        }

        client.get("/request")
        assertEquals(2, onCallProcessedTimes)
    }

    @Test
    fun test_routing_scoped_install() = testApplication {
        class Config(var data: String)

        val plugin = createRouteScopedPlugin("F", { Config("default") }) {
            onCall { call ->
                call.respond(pluginConfig.data)
            }
        }

        routing {
            get { }
            route("/top") {
                install(plugin) { data = "/top" }

                get { }

                route("/nested1") {
                    install(plugin) { data = "/nested1" }

                    get { }

                    route("/nested") {
                        install(plugin)

                        get { }
                    }
                }
                route("/nested2") {
                    install(plugin) { data = "/nested2" }

                    get { }
                }
            }
        }

        assertEquals("/top", client.get("/top").bodyAsText())
        assertEquals("/nested1", client.get("/top/nested1").bodyAsText())
        assertEquals("/nested2", client.get("/top/nested2").bodyAsText())
        assertEquals("default", client.get("/top/nested1/nested").bodyAsText())
    }

    @Suppress("UNUSED_VARIABLE")
    @Test
    fun test_routing_scoped_install_can_access_route() = testApplication {
        // check that route property exists in all builders
        val pluginWithConfig = createRouteScopedPlugin("A", ::FConfig) {
            val path = route?.toString() ?: "no route"
        }
        val pluginWithConfigAndConfigPath = createRouteScopedPlugin("A", "configPath", { FConfig() }) {
            val path = route?.toString() ?: "no route"
        }
        val plugin1 = createRouteScopedPlugin("A") {
            val path = route?.toString() ?: "no route"
            onCall { call ->
                call.response.headers.append("PATH-1", path)
            }
        }

        val plugin2 = createRouteScopedPlugin("B") {
            val path = route?.toString() ?: "no route"
            onCall { call ->
                call.response.headers.append("PATH-2", path)
            }
        }

        install(plugin1)
        routing {
            get {
                call.respond("OK")
            }
            route("/a") {
                install(plugin2)
                get {
                    call.respond("OK")
                }
            }
        }

        val response1 = client.get("/")
        assertEquals("no route", response1.headers["PATH-1"])
        assertNull(response1.headers["PATH-2"])

        val response2 = client.get("/a")
        assertEquals("no route", response2.headers["PATH-1"])
        assertEquals("/a", response2.headers["PATH-2"])
    }

    @Test
    fun test_side_effect_of_install_called_on_every_installation() = testApplication {
        var globalSideEffect = ""
        val plugin = createRouteScopedPlugin("P") {
            globalSideEffect += "Called!"
        }

        routing {
            route("/1") {
                install(plugin)
            }
            route("/2") {
                install(plugin)
            }
        }
        startApplication()
        assertEquals("Called!Called!", globalSideEffect)
    }

    @Test
    fun testTransformBody() = testApplication {
        data class MyInt(val x: Int)

        val plugin = createApplicationPlugin("F") {
            onCallReceive { _ ->
                transformBody { data ->
                    val type = requestedType?.type!!
                    if (type != MyInt::class) return@transformBody data

                    MyInt(data.readInt())
                }
            }
            onCallRespond { _, _ ->
                transformBody { data ->
                    if (data !is MyInt) return@transformBody data

                    return@transformBody ByteChannel(false).apply {
                        writeInt(data.x)
                        close()
                    }
                }
            }
        }

        install(plugin)
        routing {
            post("/receive") {
                val data = call.receive<MyInt>()
                val newData = MyInt(data.x + 1)
                call.respond(newData)
            }
        }

        val call = client.post("/receive") {
            setBody(ByteReadChannel(buildPacket { writeInt(100501) }))
        }

        assertEquals(100502, call.bodyAsChannel().readInt())
    }
}
