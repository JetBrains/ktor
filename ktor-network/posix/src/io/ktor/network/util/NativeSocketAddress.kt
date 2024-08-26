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
internal sealed class NativeSocketAddress(val family: UByte) {
    @OptIn(ExperimentalForeignApi::class)
    internal abstract fun nativeAddress(block: (address: CPointer<sockaddr>, size: UInt) -> Unit)
}

/**
 * Represents an INET socket address.
 */
internal abstract class NativeInetSocketAddress(
    family: UByte,
    val port: Int
) : NativeSocketAddress(family) {
    internal abstract val ipString: String
}

internal expect class NativeIPv4SocketAddress : NativeInetSocketAddress {
    @OptIn(ExperimentalForeignApi::class)
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: UInt) -> Unit)
    override val ipString: String
}

internal expect class NativeIPv6SocketAddress : NativeInetSocketAddress {
    @OptIn(ExperimentalForeignApi::class)
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: UInt) -> Unit)
    override val ipString: String
}

/**
 * Represents an UNIX socket address.
 */
internal class NativeUnixSocketAddress(
    family: UByte,
    val path: String,
) : NativeSocketAddress(family) {
    @OptIn(ExperimentalForeignApi::class)
    override fun nativeAddress(block: (address: CPointer<sockaddr>, size: UInt) -> Unit) {
        pack_sockaddr_un(family.convert(), path) { address, size ->
            block(address, size)
        }
    }
}

internal fun NativeSocketAddress.toSocketAddress(): SocketAddress = when (this) {
    is NativeInetSocketAddress -> InetSocketAddress(ipString, port)
    is NativeUnixSocketAddress -> UnixSocketAddress(path)
}
