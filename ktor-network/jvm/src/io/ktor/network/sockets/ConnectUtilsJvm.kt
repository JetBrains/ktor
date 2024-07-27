/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.selector.eventgroup.EventGroupSelectorManager
import java.net.*
import java.nio.channels.*
import java.nio.channels.spi.*

internal actual suspend fun connect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = selector.buildOrClose({ openSocketChannelFor(remoteAddress) }) {
    if (remoteAddress is InetSocketAddress) assignOptions(socketOptions)
    nonBlocking()

    SocketImpl(this, selector, socketOptions).apply {
        connect(remoteAddress.toJavaAddress())
    }
}

internal actual fun bind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = selector.buildOrClose({ openServerSocketChannelFor(localAddress) }) {
    require(selector is EventGroupSelectorManager)

    if (localAddress is InetSocketAddress) assignOptions(socketOptions)
    nonBlocking()

    val registered = selector.group.registerChannel(this)

    ServerSocketImpl(registered, selector).apply {
        if (java7NetworkApisAvailable) {
            channel.bind(localAddress?.toJavaAddress(), socketOptions.backlogSize)
        } else {
            channel.socket().bind(localAddress?.toJavaAddress(), socketOptions.backlogSize)
        }
    }
}

internal fun SelectorProvider.openSocketChannelFor(address: SocketAddress) = when (address) {
    is InetSocketAddress -> openSocketChannel()
    is UnixSocketAddress -> {
        val selectorProviderClass = SelectorProvider::class.java
        val protocolFamilyClass = ProtocolFamily::class.java
        val unixProtocolFamily = StandardProtocolFamily.valueOf("UNIX")
        val openSocketChannelMethod = selectorProviderClass.getMethod("openSocketChannel", protocolFamilyClass)
        openSocketChannelMethod.invoke(this, unixProtocolFamily) as SocketChannel
    }
}

internal fun SelectorProvider.openServerSocketChannelFor(address: SocketAddress?) = when (address) {
    null -> openServerSocketChannel()
    is InetSocketAddress -> openServerSocketChannel()
    is UnixSocketAddress -> {
        val selectorProviderClass = SelectorProvider::class.java
        val protocolFamilyClass = ProtocolFamily::class.java
        val unixProtocolFamily = StandardProtocolFamily.valueOf("UNIX")
        val openSocketChannelMethod = selectorProviderClass.getMethod("openServerSocketChannel", protocolFamilyClass)
        openSocketChannelMethod.invoke(this, unixProtocolFamily) as ServerSocketChannel
    }
}
