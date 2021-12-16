/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.date.*

internal expect suspend fun connect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions,
    clock: GMTClock
): Socket

internal expect fun bind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions,
    clock: GMTClock
): ServerSocket
