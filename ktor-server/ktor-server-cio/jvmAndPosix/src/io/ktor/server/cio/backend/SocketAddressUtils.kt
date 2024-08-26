/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.network.sockets.*
import io.ktor.util.network.*

internal val SocketAddress.port: Int
    get() {
        val inetAddress = this as? InetSocketAddress ?: error("Expected inet socket address")
        return inetAddress.port
    }

internal expect fun SocketAddress.toNetworkAddress(): NetworkAddress
