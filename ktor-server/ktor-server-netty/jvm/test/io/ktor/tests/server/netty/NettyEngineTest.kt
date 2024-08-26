/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.cio.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.server.testing.suites.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class NettyCompressionTest : CompressionTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyContentTest : ContentTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyHttpServerCommonTest :
    HttpServerCommonTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyHttpServerJvmTest :
    HttpServerJvmTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
        configuration.tcpKeepAlive = true
    }
}

class NettyHttp2ServerCommonTest :
    HttpServerCommonTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyHttp2ServerJvmTest :
    HttpServerJvmTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
        enableHttp2 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyDisabledHttp2Test :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp2 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp2 = false
    }

    @Test
    fun testRequestWithDisabledHttp2() = runTest {
        createAndStartServer {
            application.routing {
                get("/") {
                    call.respondText("Hello, world")
                }
            }
        }

        withUrl("/") {
            assertEquals("Hello, world", bodyAsText())
            assertEquals(HttpProtocolVersion.HTTP_1_1, version)
        }
    }
}

class NettySustainabilityTest : SustainabilityTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
    Netty
) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }

    @Test
    fun testRawWebSocketFreeze() = runTest {
        createAndStartServer {
            application.install(WebSockets)
            webSocket("/ws") {
                repeat(10) {
                    send(Frame.Text("hi"))
                }
            }
        }

        val client = HttpClient(CIO) {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        var count = 0

        client.wsRaw(path = "/ws", port = port) {
            incoming.consumeAsFlow().collect { count++ }
        }

        assertEquals(11, count)
    }
}

class NettyConfigTest : ConfigTestSuite(Netty)

class NettyConnectionTest : ConnectionTestSuite(Netty)

class NettyClientCertTest : ClientCertTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyServerPluginsTest : ServerPluginsTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
    Netty
) {
    init {
        enableSsl = false
        enableHttp2 = false
    }
}
