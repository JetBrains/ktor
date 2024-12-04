/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.network

import java.net.*

/**
 * Represents remote endpoint with [hostname] and [port].
 *
 * The address will be resolved after construction.
 *
 * @throws UnresolvedAddressException if the [hostname] cannot be resolved.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.network.NetworkAddress)
 */
public actual typealias NetworkAddress = SocketAddress

public actual val NetworkAddress.hostname: String
    get() = (this as? InetSocketAddress)?.hostName ?: (this as? InetSocketAddress)?.address?.hostName ?: ""

public actual val NetworkAddress.address: String
    get() = (this as? InetSocketAddress)?.hostString ?: ""

public actual val NetworkAddress.port: Int
    get() = (this as? InetSocketAddress)?.port ?: 0

public actual typealias UnresolvedAddressException = java.nio.channels.UnresolvedAddressException

public actual fun NetworkAddress(hostname: String, port: Int): NetworkAddress {
    return InetSocketAddress(hostname, port)
}
