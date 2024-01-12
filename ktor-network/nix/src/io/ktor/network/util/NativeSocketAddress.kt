/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import io.ktor.network.sockets.*
import kotlinx.cinterop.*
import platform.posix.*

/**
 * Represents a native socket address.
 */
@OptIn(UnsafeNumber::class)
internal sealed class NativeSocketAddress(val family: sa_family_t) {
    @OptIn(ExperimentalForeignApi::class)
    internal abstract fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit)
}

/**
 * Represents an INET socket address.
 */
@OptIn(UnsafeNumber::class)
internal sealed class NativeInetSocketAddress(
    family: sa_family_t,
    val port: Int
) : NativeSocketAddress(family) {
    internal abstract val ipString: String
}

@OptIn(UnsafeNumber::class)
internal class NativeIPv4SocketAddress(
    family: sa_family_t,
    rawAddress: in_addr,
    port: Int
) : NativeInetSocketAddress(family, port) {
    val address: in_addr_t = rawAddress.s_addr

    override fun toString(): String = "NativeIPv4SocketAddress[$ipString:$port]"

    @OptIn(ExperimentalForeignApi::class)
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit) {
        cValue<sockaddr_in> {
            sin_addr.s_addr = address
            sin_port = hostToNetworkOrder(port.toUShort())
            sin_family = family

            block(ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override val ipString: String
        get() = memScoped {
            val string = allocArray<ByteVar>(INET_ADDRSTRLEN)
            val value = cValue<in_addr> {
                s_addr = address
            }

            ktor_inet_ntop(
                family.convert(),
                value,
                string.reinterpret(),
                INET_ADDRSTRLEN.convert()
            )?.toKString() ?: error("Failed to convert address to text")
        }
}

@OptIn(UnsafeNumber::class)
internal class NativeIPv6SocketAddress(
    family: sa_family_t,
    private val rawAddress: in6_addr,
    port: Int,
    private val flowInfo: uint32_t,
    private val scopeId: uint32_t
) : NativeInetSocketAddress(family, port) {
    @OptIn(ExperimentalForeignApi::class)
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit) {
        cValue<sockaddr_in6> {
            sin6_family = family
            sin6_flowinfo = flowInfo
            sin6_port = hostToNetworkOrder(port.toUShort())
            sin6_scope_id = scopeId

            block(ptr.reinterpret(), sizeOf<sockaddr_in6>().convert())
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override val ipString: String
        get() = memScoped {
            val string = allocArray<ByteVar>(INET6_ADDRSTRLEN)
            ktor_inet_ntop(
                family.convert(),
                rawAddress.ptr,
                string.reinterpret(),
                INET6_ADDRSTRLEN.convert()
            )?.toKString()
                ?: error("Failed to convert address to text")
        }
}

/**
 * Represents an UNIX socket address.
 */
@OptIn(UnsafeNumber::class)
internal class NativeUnixSocketAddress(
    family: sa_family_t,
    val path: String,
) : NativeSocketAddress(family) {
    @OptIn(ExperimentalForeignApi::class)
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit) {
        pack_sockaddr_un(family.convert(), path) { address, size ->
            block(address, size)
        }
    }
}

internal fun NativeSocketAddress.toSocketAddress(): SocketAddress = when (this) {
    is NativeInetSocketAddress -> InetSocketAddress(ipString, port)
    is NativeUnixSocketAddress -> UnixSocketAddress(path)
}
