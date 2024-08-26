/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.AF_INET
import platform.posix.AF_UNSPEC
import platform.posix.FIONBIO
import platform.posix.SOCK_STREAM
import platform.posix.WSAEINPROGRESS
import platform.posix.WSAEWOULDBLOCK
import platform.posix.WSAGetLastError
import platform.posix.WSAStartup
import platform.posix.accept
import platform.posix.bind
import platform.posix.connect
import platform.posix.getpeername
import platform.posix.getsockname
import platform.posix.getsockopt
import platform.posix.ioctlsocket
import platform.posix.listen
import platform.posix.recv
import platform.posix.recvfrom
import platform.posix.setsockopt
import platform.posix.shutdown
import platform.posix.socket
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
private val initSocketsIfNeeded by lazy {
    memScoped {
        val lpWSAData = alloc<WSADATA>()
        WSAStartup(0x0202u, lpWSAData.ptr).check { it == 0 }
    }
}

internal actual fun initSocketsIfNeeded() {
    initSocketsIfNeeded
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getAddressInfo(
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
        .check { it == 0 }

    defer { freeaddrinfo(result.value) }
    return result.pointed.toIpList()
}

@OptIn(ExperimentalForeignApi::class)
private fun addrinfo?.toIpList(): List<NativeSocketAddress> {
    var current: addrinfo? = this
    val result = mutableListOf<NativeSocketAddress>()

    while (current != null) {
        result += current.ai_addr!!.pointed.toNativeSocketAddress()
        current = current.ai_next?.pointed
    }

    return result
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun sockaddr.toNativeSocketAddress(): NativeSocketAddress = when (sa_family.toInt()) {
    AF_INET -> {
        val address = ptr.reinterpret<sockaddr_in>().pointed
        NativeIPv4SocketAddress(
            address.sin_family.convert(),
            address.sin_addr,
            networkToHostOrder(address.sin_port).toInt()
        )
    }

    AF_INET6 -> {
        val address = ptr.reinterpret<sockaddr_in6>().pointed
        NativeIPv6SocketAddress(
            address.sin6_family.convert(),
            address.sin6_addr,
            networkToHostOrder(address.sin6_port).toInt(),
            address.sin6_flowinfo,
            address.sin6_scope_id
        )
    }

    platform.posix.AF_UNIX -> {
        unpack_sockaddr_un(this) { family, path ->
            NativeUnixSocketAddress(family.convert(), path)
        }
    }

    else -> error("Unknown address family $sa_family")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_inet_ntop(
    family: Int,
    src: CPointer<*>?,
    dst: CPointer<ByteVar>?,
    size: UInt
): CPointer<ByteVar>? = inet_ntop(family, src, dst, size.convert())

internal actual fun <T> unpack_sockaddr_un(
    sockaddr: sockaddr,
    block: (family: UShort, path: String) -> T
): T {
    error("Address ${sockaddr.sa_family} is not supported on Windows")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun pack_sockaddr_un(
    family: UShort,
    path: String,
    block: (address: CPointer<sockaddr>, size: UInt) -> Unit
) {
    error("Address $family is not supported on Windows")
}

internal actual val reusePortFlag: Int? = null // Unsupported on Windows

internal actual object ShutdownCommands {
    actual val Receive: Int = SD_RECEIVE
    actual val Send: Int = SD_SEND
    actual val Both: Int = SD_BOTH
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_shutdown(fd: Int, how: Int): Int {
    return shutdown(fd.convert(), how)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun nonBlocking(descriptor: Int): Int {
    memScoped {
        val argp = alloc<UIntVar>()
        argp.value = 1u
        return ioctlsocket(
            descriptor.convert(),
            FIONBIO.convert(),
            argp.ptr
        )
    }
}

internal actual fun signalIgnoreSigpipe() {
    // Unsupported on Windows
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_send(socket: Int, buf: CValuesRef<ByteVar>?, len: Int, flags: Int): Int {
    return send(socket.convert(), buf, len, flags)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_recv(socket: Int, buf: CValuesRef<ByteVar>?, len: Int, flags: Int): Int {
    return recv(socket.convert(), buf, len, flags)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_sendto(
    __fd: Int,
    __buf: CValuesRef<ByteVar>?,
    __n: UInt,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: UInt
): Int {
    return sendto(__fd.convert(), __buf, __n.convert(), __flags, __addr, __addr_len.convert())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_recvfrom(
    __fd: Int,
    __buf: CValuesRef<ByteVar>?,
    __n: UInt,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CPointer<UIntVar>?
): Int {
    return recvfrom(__fd.convert(), __buf, __n.convert(), __flags, __addr, __addr_len?.reinterpret())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_socket(__domain: Int, __type: Int, __protocol: Int): Int {
    return socket(__domain, __type, __protocol).convert()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_bind(__fd: Int, __addr: CValuesRef<sockaddr>?, __len: UInt): Int {
    return bind(__fd.convert(), __addr, __len.convert())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_connect(__fd: Int, __addr: CValuesRef<sockaddr>?, __len: UInt): Int {
    return connect(__fd.convert(), __addr, __len.convert())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_accept(__fd: Int, __addr: CValuesRef<sockaddr>?, __addr_len: CPointer<UIntVar>?): Int {
    return accept(__fd.convert(), __addr, __addr_len?.reinterpret()).convert()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_listen(__fd: Int, __n: Int): Int {
    return listen(__fd.convert(), __n)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_setsockopt(
    __fd: Int,
    __level: Int,
    __optname: Int,
    __optval: CPointer<*>?,
    __optlen: UInt
): Int {
    return setsockopt(
        __fd.convert(),
        __level,
        __optname,
        __optval?.reinterpret<ByteVar>()?.toKString(),
        __optlen.convert()
    )
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_getsockopt(
    __fd: Int,
    __level: Int,
    __optname: Int,
    __optval: CPointer<*>?,
    __optlen: CPointer<UIntVar>?
): Int {
    return getsockopt(__fd.convert(), __level, __optname, __optval?.reinterpret(), __optlen?.reinterpret())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_getsockname(
    __fd: Int,
    __addr: CValuesRef<sockaddr>?,
    __len: CPointer<UIntVar>?
): Int {
    return getsockname(__fd.convert(), __addr, __len?.reinterpret())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_getpeername(
    __fd: Int,
    __addr: CValuesRef<sockaddr>?,
    __len: CPointer<UIntVar>?
): Int {
    return getpeername(__fd.convert(), __addr, __len?.reinterpret())
}

internal actual fun getSocketError(): Int {
    return WSAGetLastError()
}

internal actual fun isWouldBlockError(error: Int): Boolean {
    return error == WSAEWOULDBLOCK || error == WSAEINPROGRESS
}
