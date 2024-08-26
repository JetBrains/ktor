/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun UDPSocketBuilder.Companion.connectUDP(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket {
    initSocketsIfNeeded()

    val address = localAddress?.address ?: getAnyLocalAddress()

    val descriptor = ktor_socket(address.family.convert(), SOCK_DGRAM, 0).check()
    val selectable = SelectableNative(descriptor)

    try {
        assignOptions(descriptor, options)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            ktor_bind(descriptor, pointer, size).check()
        }

        remoteAddress.address.nativeAddress { pointer, size ->
            ktor_connect(descriptor, pointer, size).check()
        }

        return DatagramSocketNative(
            descriptor = descriptor,
            selector = selector,
            selectable = selectable,
            remote = remoteAddress,
            parent = selector.coroutineContext
        )
    } catch (throwable: Throwable) {
        ktor_shutdown(descriptor, ShutdownCommands.Both)
        // Descriptor is closed by the selector manager
        selector.notifyClosed(selectable)
        throw throwable
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun UDPSocketBuilder.Companion.bindUDP(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket {
    initSocketsIfNeeded()

    val address = localAddress?.address ?: getAnyLocalAddress()

    val descriptor = ktor_socket(address.family.convert(), SOCK_DGRAM, 0).check()
    val selectable = SelectableNative(descriptor)

    try {
        assignOptions(descriptor, options)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            ktor_bind(descriptor, pointer, size).check()
        }

        return DatagramSocketNative(
            descriptor = descriptor,
            selector = selector,
            selectable = selectable,
            remote = null,
            parent = selector.coroutineContext
        )
    } catch (throwable: Throwable) {
        ktor_shutdown(descriptor, ShutdownCommands.Both)
        // Descriptor is closed by the selector manager
        selector.notifyClosed(selectable)
        throw throwable
    }
}
