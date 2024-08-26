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
            val channelClass = getChannelClass()

            return when {
                KQueue.isAvailable() -> EventLoopGroupProxy(
                    channelClass,
                    KQueueEventLoopGroup(parallelism, defaultFactory)
                )

                Epoll.isAvailable() -> EventLoopGroupProxy(
                    channelClass,
                    EpollEventLoopGroup(parallelism, defaultFactory)
                )

                else -> EventLoopGroupProxy(channelClass, NioEventLoopGroup(parallelism, defaultFactory))
            }
        }
    }
}
