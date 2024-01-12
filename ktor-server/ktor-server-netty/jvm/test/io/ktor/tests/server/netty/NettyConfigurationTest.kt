/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.logging.*
import io.mockk.*
import io.netty.channel.*
import io.netty.channel.nio.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.test.*

class NettyConfigurationTest {
    private fun server(
        block: NettyApplicationEngine.Configuration.() -> Unit
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        val config = MapApplicationConfig()
        val events = Events()

        val server = mockk<EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>>()
        val application = mockk<Application>().apply {
            every { coroutineContext } returns Job()
            every { developmentMode } returns false
            every { parentCoroutineContext } returns Dispatchers.Default
        }
        val environment = mockk<ApplicationEnvironment>().apply {
            every { log } returns KtorSimpleLogger("test-logger")
            every { this@apply.config } returns config
        }
        every { server.application } returns application
        every { server.environment } returns environment
        every { server.monitor } returns events
        every { server.engineConfig } returns NettyApplicationEngine.Configuration()
            .apply { connector { port = 0 } }
            .apply(block)
        return server
    }

    @Test
    fun configuredChildAndParentGroupShutdownGracefully() {
        val parentGroup = spyk(NioEventLoopGroup())
        val childGroup = spyk(NioEventLoopGroup())
        val server = server {
            configureBootstrap = {
                group(parentGroup, childGroup)
            }
        }

        val engine = NettyApplicationEngine(
            server.environment,
            server.monitor,
            server.application.developmentMode,
            server.engineConfig
        ) { server.application }

        engine.start(wait = false)
        engine.stop(10, 10)
        verify { parentGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS) }
        verify { childGroup.shutdownGracefully(10, 10, TimeUnit.MILLISECONDS) }
    }

    @Test
    fun configuredChannelEventuallyClosed() {
        val resultChannel = stubChannel()

        val promise = mockk<ChannelPromise>()
        every { promise.channel() } returns resultChannel
        every { promise.sync() } returns promise

        val channel = stubChannel()
        every { channel.newPromise() } returns promise

        val factory = mockk<ChannelFactory<ServerChannel>>()
        every { factory.newChannel() } returns channel

        val group = stubGroup(channel)

        val server = server {
            configureBootstrap = {
                channelFactory(factory)
                group(group)
            }
        }

        val engine = NettyApplicationEngine(
            server.environment,
            server.monitor,
            server.application.developmentMode,
            server.engineConfig
        ) { server.application }

        engine.start(wait = false)
        engine.stop(10, 10)

        verify { resultChannel.close() }
    }

    private fun stubGroup(channel: Channel): EventLoopGroup {
        return mockk {
            every { register(channel) } returns stubResolvedFuture(channel)
            every { shutdownGracefully(any(), any(), any()) } returns mockk {
                every { await() } returns mockk()
            }
        }
    }

    private fun stubResolvedFuture(channel: Channel): ChannelFuture {
        return mockk {
            every { cause() } returns null
            every { channel() } returns channel
            every { isDone } returns true
            every { isSuccess } returns true
        }
    }

    private fun stubChannel(): ServerChannel {
        return mockk {
            every { unsafe() } returns mockk {
                every { closeForcibly() } just Runs
                every { register(any(), any()) } just Runs
            }
            every { pipeline() } returns mockk {
                every { addLast(*anyVararg()) } returns this
            }
            every { isRegistered } returns true
            every { eventLoop() } returns DefaultEventLoop()
            every { localAddress() } returns mockk()
            every { isOpen } returns true
            every { bind(any(), any()) } returns mockk {
                every { addListener(any()) } returns mockk()
            }
            every { close() } returns mockk {
                every { sync() } returns mockk()
            }
        }
    }
}
