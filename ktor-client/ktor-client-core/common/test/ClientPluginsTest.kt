/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.test.dispatcher.*
import kotlin.test.*

class ClientPluginsTest {

    @Test
    fun testEmptyPluginDoesNotBreakPipeline() = testSuspend {
        val plugin = createClientPlugin("F", createConfiguration = {}) {
        }

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("OK")
                }
            }

            install(plugin)
        }

        assertEquals("OK", client.get("/").bodyAsText())
    }

    @Test
    fun testPluginOnRequestOnResponseInterception() = testSuspend {
        data class Config(var enabled: Boolean = false)

        var onResponseCalled = false
        val plugin = createClientPlugin("F", ::Config) {
            val enabled = pluginConfig.enabled
            onRequest { request, _ ->
                if (enabled) {
                    request.headers.append("X-Test", "true")
                }
            }
            onResponse { response ->
                if (enabled) {
                    assertEquals("true", response.headers["X-Test"])
                    onResponseCalled = true
                }
            }
        }

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    assertEquals("true", it.headers["X-Test"])
                    respond("OK", headers = headersOf("X-Test" to listOf("true")))
                }
            }

            install(plugin) {
                enabled = true
            }
        }

        assertEquals("OK", client.get("/").bodyAsText())
        assertTrue(onResponseCalled)
    }

    @Test
    fun testSamePhaseDefinedTwice() = testSuspend {
        var onCallProcessedTimes = 0
        val plugin = createClientPlugin("F") {
            onRequest { _, _ ->
                onCallProcessedTimes += 1
            }

            onRequest { _, _ ->
                onCallProcessedTimes += 1
            }
        }

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("OK")
                }
            }

            install(plugin)
        }

        assertEquals("OK", client.get("/").bodyAsText())
        assertEquals(2, onCallProcessedTimes)
    }

    @Test
    fun testTransformBody() = testSuspend {
        val plugin = createClientPlugin("F") {
            transformRequestBody { _, content, bodyType ->
                assertEquals(String::class, bodyType!!.type)
                TextContent("$content!", ContentType.Text.Plain)
            }
            transformResponseBody { _, content, requestedType ->
                assertEquals(String::class, requestedType.type)
                content.readRemaining().readText() + "!"
            }
        }

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    assertEquals("hello!", (it.body as TextContent).text)
                    respond("hi")
                }
            }

            install(plugin)
        }

        val response = client.post("/") {
            setBody("hello")
        }.body<String>()
        assertEquals("hi!", response)
    }

    object CustomHook : ClientHook<() -> Unit> {
        override fun install(client: HttpClient, handler: () -> Unit) {
            client.requestPipeline.intercept(HttpRequestPipeline.State) {
                handler()
            }
        }
    }

    @Test
    fun testCustomHook() = testSuspend {
        var hookCalled = false
        val plugin = createClientPlugin("F") {
            on(CustomHook) {
                hookCalled = true
            }
        }

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("OK")
                }
            }

            install(plugin)
        }

        assertEquals("OK", client.get("/").bodyAsText())
        assertTrue(hookCalled)
    }

    @Test
    fun testSendHook() = testSuspend {
        val plugin = createClientPlugin("F") {
            on(Send) {
                val call = proceed(it)
                assertEquals("temp response", call.response.bodyAsText())
                proceed(it)
            }
        }

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("temp response")
                }
                addHandler {
                    respond("OK")
                }
            }

            install(plugin)
        }

        assertEquals("OK", client.get("/").bodyAsText())
    }
}
