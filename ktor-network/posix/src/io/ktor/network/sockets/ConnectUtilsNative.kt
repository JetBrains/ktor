/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.io.IOException
import platform.posix.*

private const val DEFAULT_BACKLOG_SIZE = 50

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual suspend fun connect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = memScoped {
    initSocketsIfNeeded()

    var lastException: PosixException? = null
    for (remote in remoteAddress.resolve()) {
        try {
            val descriptor: Int = ktor_socket(remote.family.convert(), SOCK_STREAM, 0).check()
            val selectable = SelectableNative(descriptor)

            try {
                assignOptions(descriptor, socketOptions)
                nonBlocking(descriptor)

                var connectResult = -1
                remote.nativeAddress { address, size ->
                    connectResult = ktor_connect(descriptor, address, size)
                }

                if (connectResult < 0 && isWouldBlockError(getSocketError())) {
                    while (true) {
                        selector.select(selectable, SelectInterest.CONNECT)
                        val result = alloc<IntVar>()
                        val size = alloc<UIntVar> {
                            value = sizeOf<IntVar>().convert()
                        }
                        ktor_getsockopt(descriptor, SOL_SOCKET, SO_ERROR, result.ptr, size.ptr).check()
                        val resultValue = result.value.toInt()
                        when {
                            resultValue == 0 -> break // connected
                            isWouldBlockError(resultValue) -> continue
                            else -> throw PosixException.forSocketError(error = resultValue)
                        }
                    }
                } else {
                    connectResult.check()
                }

                val localAddress = getLocalAddress(descriptor)

                return TCPSocketNative(
                    descriptor,
                    selector,
                    selectable,
                    remoteAddress = remote.toSocketAddress(),
                    localAddress = localAddress.toSocketAddress()
                )
            } catch (throwable: Throwable) {
                ktor_shutdown(descriptor, ShutdownCommands.Both)
                // Descriptor is closed by the selector manager
                selector.notifyClosed(selectable)
                throw throwable
            }
        } catch (exception: PosixException) {
            lastException = exception
        }
    }

    throw IOException("Failed to connect to $remoteAddress.", lastException)
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun bind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = memScoped {
    initSocketsIfNeeded()

    val address = localAddress?.address ?: getAnyLocalAddress()
    val descriptor = ktor_socket(address.family.convert(), SOCK_STREAM, 0).check()
    val selectable = SelectableNative(descriptor)

    try {
        assignOptions(descriptor, socketOptions)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            ktor_bind(descriptor, pointer, size).check()
        }

        ktor_listen(descriptor, DEFAULT_BACKLOG_SIZE).check()

        val resolvedLocalAddress = getLocalAddress(descriptor)

        return TCPServerSocketNative(
            descriptor,
            selector,
            selectable,
            localAddress = resolvedLocalAddress.toSocketAddress(),
            parent = selector.coroutineContext
        )
    } catch (throwable: Throwable) {
        ktor_shutdown(descriptor, ShutdownCommands.Both)
        // Descriptor is closed by the selector manager
        selector.notifyClosed(selectable)
        throw throwable
    }
}
