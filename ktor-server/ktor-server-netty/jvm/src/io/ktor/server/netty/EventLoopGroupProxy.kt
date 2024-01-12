/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.netty.channel.*
import io.netty.channel.epoll.*
import io.netty.channel.kqueue.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.util.concurrent.*
import java.lang.reflect.*
import java.util.concurrent.*
import kotlin.reflect.*

/**
 * Transparently allows for the creation of [EventLoopGroup]'s utilising the optimal implementation for
 * a given operating system, subject to availability, or falling back to [NioEventLoopGroup] if none is available.
 */
public class EventLoopGroupProxy(
    public val channel: KClass<out ServerSocketChannel>,
    group: EventLoopGroup
) : EventLoopGroup by group {

    public companion object {

        public fun create(parallelism: Int): EventLoopGroupProxy {
            val defaultFactory = DefaultThreadFactory(EventLoopGroupProxy::class.java, true)

            val factory = ThreadFactory { runnable ->
                defaultFactory.newThread {
                    markParkingProhibited()
                    runnable.run()
                }
            }

            val channelClass = getChannelClass()

            return when {
                KQueue.isAvailable() -> EventLoopGroupProxy(channelClass, KQueueEventLoopGroup(parallelism, factory))
                Epoll.isAvailable() -> EventLoopGroupProxy(channelClass, EpollEventLoopGroup(parallelism, factory))
                else -> EventLoopGroupProxy(channelClass, NioEventLoopGroup(parallelism, factory))
            }
        }

        private val prohibitParkingFunction: Method? by lazy {
            try {
                Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
                    .getMethod("prohibitParking")
            } catch (cause: Throwable) {
                null
            }
        }

        private fun markParkingProhibited() {
            try {
                prohibitParkingFunction?.invoke(null)
            } catch (_: Throwable) {
            }
        }
    }
}
