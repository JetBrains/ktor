/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun initSocketsIfNeeded() {}

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

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
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

    AF_UNIX -> {
        unpack_sockaddr_un(this) { family, path ->
            NativeUnixSocketAddress(family.convert(), path)
        }
    }

    else -> error("Unknown address family $sa_family")
}

internal actual val reusePortFlag: Int? = SO_REUSEPORT

internal actual object ShutdownCommands {
    actual val Receive: Int = SHUT_RD
    actual val Send: Int = SHUT_WR
    actual val Both: Int = SHUT_RDWR
}

internal actual fun ktor_shutdown(fd: Int, how: Int): Int {
    return shutdown(fd, how)
}

internal actual fun nonBlocking(descriptor: Int): Int {
    return fcntl(descriptor, F_SETFL, O_NONBLOCK)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun signalIgnoreSigpipe() {
    signal(SIGPIPE, SIG_IGN)
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun ktor_send(socket: Int, buf: CValuesRef<ByteVar>?, len: Int, flags: Int): Int {
    return send(socket, buf, len.convert(), flags).convert()
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun ktor_recv(socket: Int, buf: CValuesRef<ByteVar>?, len: Int, flags: Int): Int {
    return recv(socket, buf, len.convert(), flags).convert()
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun ktor_sendto(
    __fd: Int,
    __buf: CValuesRef<ByteVar>?,
    __n: UInt,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: UInt
): Int {
    return sendto(__fd, __buf, __n.convert(), __flags, __addr, __addr_len).convert()
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun ktor_recvfrom(
    __fd: Int,
    __buf: CValuesRef<ByteVar>?,
    __n: UInt,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CPointer<UIntVar>?
): Int {
    return recvfrom(__fd, __buf, __n.convert(), __flags, __addr, __addr_len).convert()
}

internal actual fun ktor_socket(__domain: Int, __type: Int, __protocol: Int): Int {
    return socket(__domain, __type, __protocol)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_bind(__fd: Int, __addr: CValuesRef<sockaddr>?, __len: UInt): Int {
    return bind(__fd, __addr, __len)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_connect(__fd: Int, __addr: CValuesRef<sockaddr>?, __len: UInt): Int {
    return connect(__fd, __addr, __len)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_accept(__fd: Int, __addr: CValuesRef<sockaddr>?, __addr_len: CPointer<UIntVar>?): Int {
    return accept(__fd, __addr, __addr_len)
}

internal actual fun ktor_listen(__fd: Int, __n: Int): Int {
    return listen(__fd, __n)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_setsockopt(
    __fd: Int,
    __level: Int,
    __optname: Int,
    __optval: CPointer<*>?,
    __optlen: UInt
): Int {
    return setsockopt(__fd, __level, __optname, __optval, __optlen)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_getsockopt(
    __fd: Int,
    __level: Int,
    __optname: Int,
    __optval: CPointer<*>?,
    __optlen: CPointer<UIntVar>?
): Int {
    return getsockopt(__fd, __level, __optname, __optval, __optlen)
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
    return errno
}

internal actual fun isWouldBlockError(error: Int): Boolean {
    return error == EAGAIN || error == EWOULDBLOCK || error == EINPROGRESS
}
