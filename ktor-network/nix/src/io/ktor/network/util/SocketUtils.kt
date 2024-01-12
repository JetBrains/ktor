/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.utils.io.bits.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.experimental.*

@OptIn(ExperimentalForeignApi::class)
internal fun getAddressInfo(
    hostname: String,
    portInfo: Int
): List<NativeSocketAddress> = memScoped {
    val hints: CValue<addrinfo> = cValue {
        ai_family = AF_UNSPEC
        ai_socktype = SOCK_STREAM
        ai_flags = AI_PASSIVE or AI_NUMERICSERV
        ai_protocol = 0
    }

    val result = alloc<CPointerVar<addrinfo>>()
    getaddrinfo(hostname, portInfo.toString(), hints, result.ptr)
        .check()

    defer { freeaddrinfo(result.value) }
    return result.pointed.toIpList()
}

@OptIn(ExperimentalForeignApi::class)
internal fun getLocalAddress(descriptor: Int): NativeSocketAddress = memScoped {
    val address = alloc<sockaddr_storage>()
    val length: UIntVarOf<UInt> = alloc()
    length.value = sizeOf<sockaddr_storage>().convert()

    getsockname(descriptor, address.ptr.reinterpret(), length.ptr).check()

    return@memScoped address.reinterpret<sockaddr>().toNativeSocketAddress()
}

@OptIn(ExperimentalForeignApi::class)
internal fun getRemoteAddress(descriptor: Int): NativeSocketAddress = memScoped {
    val address = alloc<sockaddr_storage>()
    val length: UIntVarOf<UInt> = alloc()
    length.value = sizeOf<sockaddr_storage>().convert()

    getpeername(descriptor, address.ptr.reinterpret(), length.ptr).check()

    return@memScoped address.reinterpret<sockaddr>().toNativeSocketAddress()
}

@OptIn(ExperimentalForeignApi::class)
internal fun addrinfo?.toIpList(): List<NativeSocketAddress> {
    var current: addrinfo? = this
    val result = mutableListOf<NativeSocketAddress>()

    while (current != null) {
        result += current.ai_addr!!.pointed.toNativeSocketAddress()
        current = current.ai_next?.pointed
    }

    return result
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal fun sockaddr.toNativeSocketAddress(): NativeSocketAddress = when (sa_family.toInt()) {
    AF_INET -> {
        val address = ptr.reinterpret<sockaddr_in>().pointed
        NativeIPv4SocketAddress(address.sin_family, address.sin_addr, networkToHostOrder(address.sin_port).toInt())
    }
    AF_INET6 -> {
        val address = ptr.reinterpret<sockaddr_in6>().pointed
        NativeIPv6SocketAddress(
            address.sin6_family,
            address.sin6_addr,
            networkToHostOrder(address.sin6_port).toInt(),
            address.sin6_flowinfo,
            address.sin6_scope_id
        )
    }
    AF_UNIX -> {
        unpack_sockaddr_un(this) { family, path ->
            NativeUnixSocketAddress(family.convert(), path)
        }
    }
    else -> error("Unknown address family $sa_family")
}

@OptIn(ExperimentalNativeApi::class)
internal fun networkToHostOrder(value: UShort): UShort {
    if (!Platform.isLittleEndian) return value
    return value.reverseByteOrder()
}

@OptIn(ExperimentalNativeApi::class)
internal fun hostToNetworkOrder(value: UShort): UShort {
    if (!Platform.isLittleEndian) return value
    return value.reverseByteOrder()
}

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_inet_ntop(
    family: Int,
    src: CValuesRef<*>?,
    dst: CValuesRef<ByteVar>?,
    size: socklen_t
): CPointer<ByteVar>?

internal expect fun <T> unpack_sockaddr_un(sockaddr: sockaddr, block: (family: UShort, path: String) -> T): T

@OptIn(ExperimentalForeignApi::class)
internal expect fun pack_sockaddr_un(
    family: UShort,
    path: String,
    block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit
)
