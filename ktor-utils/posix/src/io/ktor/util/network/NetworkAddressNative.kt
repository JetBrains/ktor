/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.network

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

/**
 * Represents remote endpoint with [hostname] and [port].
 *
 * The address will be resolved after construction.
 *
 * @throws UnresolvedAddressException if the [hostname] cannot be resolved.
 */
public actual abstract class NetworkAddress(
    public val hostname: String,
    public val port: Int,
    explicitAddress: Any? = null
) {
    private val _explicitAddress: AtomicRef<Any?> = atomic(explicitAddress)

    @InternalAPI
    public var explicitAddress: Any? by _explicitAddress

    /**
     * Resolve current socket address.
     */

    override fun toString(): String = "NetworkAddress[$hostname:$port]"
}

public actual fun NetworkAddress(hostname: String, port: Int): NetworkAddress =
    object : NetworkAddress(hostname, port, null) {}

@Suppress("FunctionName")
public fun ResolvedNetworkAddress(hostname: String, port: Int, explicitAddress: Any?): NetworkAddress =
    object : NetworkAddress(hostname, port, explicitAddress) {}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public actual val NetworkAddress.hostname: String
    get() = hostname

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public actual val NetworkAddress.address: String
    get() = hostname

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public actual val NetworkAddress.port: Int
    get() = port

public actual class UnresolvedAddressException : IllegalArgumentException()
