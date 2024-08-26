/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.util

import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*

internal actual class NativeIPv4SocketAddress(
    family: UByte,
    rawAddress: in_addr,
    port: Int
) : NativeInetSocketAddress(family, port) {
    private val address: u_long = rawAddress.S_un.S_addr

    override fun toString(): String = "NativeIPv4SocketAddress[$ipString:$port]"

    @OptIn(ExperimentalForeignApi::class)
    actual override fun nativeAddress(block: (address: CPointer<sockaddr>, size: UInt) -> Unit) {
        cValue<sockaddr_in> {
            sin_addr.S_un.S_addr = address
            sin_port = hostToNetworkOrder(port.toUShort())
            sin_family = family.toShort()

            block(ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual override val ipString: String
        get() = memScoped {
            val string = allocArray<ByteVar>(INET_ADDRSTRLEN)
            val value = cValue<in_addr> {
                S_un.S_addr = address
            }

            ktor_inet_ntop(
                family.convert(),
                value.ptr,
                string.reinterpret(),
                INET_ADDRSTRLEN.convert()
            )?.toKString() ?: error("Failed to convert address to text")
        }
}

internal actual class NativeIPv6SocketAddress(
    family: UByte,
    private val rawAddress: in6_addr,
    port: Int,
    private val flowInfo: uint32_t,
    private val scopeId: uint32_t
) : NativeInetSocketAddress(family, port) {

    override fun toString(): String = "NativeIPv6SocketAddress[$ipString:$port]"

    @OptIn(ExperimentalForeignApi::class)
    actual override fun nativeAddress(block: (address: CPointer<sockaddr>, size: UInt) -> Unit) {
        cValue<sockaddr_in6> {
            sin6_family = family.convert()
            sin6_flowinfo = flowInfo
            sin6_port = hostToNetworkOrder(port.toUShort())
            sin6_scope_id = scopeId

            block(ptr.reinterpret(), sizeOf<sockaddr_in6>().convert())
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual override val ipString: String
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
