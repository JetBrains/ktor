/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.util.*
import kotlinx.cinterop.*
import platform.posix.*

internal fun nonBlocking(descriptor: Int) {
    fcntl(descriptor, F_SETFL, O_NONBLOCK).check { it == 0 }
}

internal fun assignOptions(descriptor: Int, options: SocketOptions) {
    setSocketFlag(descriptor, SO_REUSEADDR, options.reuseAddress)
    setSocketFlag(descriptor, SO_REUSEPORT, options.reusePort)
    if (options is SocketOptions.UDPSocketOptions) {
        setSocketFlag(descriptor, SO_BROADCAST, options.broadcast)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun setSocketFlag(
    descriptor: Int,
    optionName: Int,
    optionValue: Boolean
) = memScoped {
    val flag = alloc<IntVar>()
    flag.value = if (optionValue) 1 else 0
    setsockopt(descriptor, SOL_SOCKET, optionName, flag.ptr, sizeOf<IntVar>().convert())
}
