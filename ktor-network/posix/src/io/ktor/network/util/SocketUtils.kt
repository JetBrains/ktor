/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.experimental.*

internal expect fun initSocketsIfNeeded()

internal expect fun getAddressInfo(
    hostname: String,
    portInfo: Int
): List<NativeSocketAddress>

@OptIn(ExperimentalForeignApi::class)
internal fun getLocalAddress(descriptor: Int): NativeSocketAddress = memScoped {
    val address = alloc<sockaddr_storage>()
    val length: UIntVarOf<UInt> = alloc()
    length.value = sizeOf<sockaddr_storage>().convert()

    ktor_getsockname(descriptor, address.ptr.reinterpret(), length.ptr).check()

    return@memScoped address.reinterpret<sockaddr>().toNativeSocketAddress()
}

@OptIn(ExperimentalForeignApi::class)
internal fun getRemoteAddress(descriptor: Int): NativeSocketAddress = memScoped {
    val address = alloc<sockaddr_storage>()
    val length: UIntVarOf<UInt> = alloc()
    length.value = sizeOf<sockaddr_storage>().convert()

    ktor_getpeername(descriptor, address.ptr.reinterpret(), length.ptr).check()

    return@memScoped address.reinterpret<sockaddr>().toNativeSocketAddress()
}

internal expect fun sockaddr.toNativeSocketAddress(): NativeSocketAddress

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
    src: CPointer<*>?,
    dst: CPointer<ByteVar>?,
    size: UInt
): CPointer<ByteVar>?

internal expect fun <T> unpack_sockaddr_un(sockaddr: sockaddr, block: (family: UShort, path: String) -> T): T

@OptIn(ExperimentalForeignApi::class)
internal expect fun pack_sockaddr_un(
    family: UShort,
    path: String,
    block: (address: CPointer<sockaddr>, size: UInt) -> Unit
)

internal expect val reusePortFlag: Int?

internal expect object ShutdownCommands {
    val Receive: Int
    val Send: Int
    val Both: Int
}

internal expect fun ktor_shutdown(fd: Int, how: Int): Int

internal expect fun nonBlocking(descriptor: Int): Int

internal expect fun signalIgnoreSigpipe()

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_send(socket: Int, buf: CValuesRef<ByteVar>?, len: Int, flags: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_recv(socket: Int, buf: CValuesRef<ByteVar>?, len: Int, flags: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_sendto(
    __fd: Int,
    __buf: CValuesRef<ByteVar>?,
    __n: UInt,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: UInt
): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_recvfrom(
    __fd: Int,
    __buf: CValuesRef<ByteVar>?,
    __n: UInt,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CPointer<UIntVarOf<UInt>>?
): Int

internal expect fun ktor_socket(__domain: Int, __type: Int, __protocol: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_bind(__fd: Int, __addr: CValuesRef<sockaddr>?, __len: UInt): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_connect(__fd: Int, __addr: CValuesRef<sockaddr>?, __len: UInt): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_accept(__fd: Int, __addr: CValuesRef<sockaddr>?, __addr_len: CPointer<UIntVar>?): Int

internal expect fun ktor_listen(__fd: Int, __n: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_setsockopt(
    __fd: Int,
    __level: Int,
    __optname: Int,
    __optval: CPointer<*>?,
    __optlen: UInt
): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_getsockopt(
    __fd: Int,
    __level: Int,
    __optname: Int,
    __optval: CPointer<*>?,
    __optlen: CPointer<UIntVar>?
): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_getsockname(
    __fd: Int,
    __addr: CValuesRef<sockaddr>?,
    __len: CPointer<UIntVar>?
): Int

@OptIn(ExperimentalForeignApi::class)
internal expect fun ktor_getpeername(
    __fd: Int,
    __addr: CValuesRef<sockaddr>?,
    __len: CPointer<UIntVar>?
): Int

internal fun PosixException.Companion.forSocketError(
    error: Int = getSocketError(),
    posixFunctionName: String? = null
): PosixException = forErrno(error, posixFunctionName)

internal expect fun getSocketError(): Int

internal expect fun isWouldBlockError(error: Int): Boolean
