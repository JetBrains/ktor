/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import java.lang.reflect.*

public actual sealed class SocketAddress {
    internal abstract val address: java.net.SocketAddress
}

public actual class InetSocketAddress internal constructor(
    override val address: java.net.InetSocketAddress
) : SocketAddress() {

    // May trigger a name service reverse lookup when called.
    public actual val hostname: String get() = address.hostName

    public actual val port: Int get() = address.port

    public actual constructor(hostname: String, port: Int) :
        this(java.net.InetSocketAddress(hostname, port))

    public actual operator fun component1(): String = hostname

    public actual operator fun component2(): Int = port

    /**
     * Create a copy of [InetSocketAddress].
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public actual fun copy(
        hostname: String,
        port: Int
    ): InetSocketAddress = InetSocketAddress(
        hostname = hostname,
        port = port
    )

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InetSocketAddress

        return address == other.address
    }

    actual override fun hashCode(): Int {
        return address.hashCode()
    }

    public actual override fun toString(): String = address.toString()
}

public actual class UnixSocketAddress internal constructor(
    override val address: java.net.SocketAddress // actually: java.net.UnixDomainSocketAddress
) : SocketAddress() {

    init {
        check(address.javaClass.name == UNIX_DOMAIN_SOCKET_ADDRESS_CLASS) {
            "address should be java.net.UnixDomainSocketAddress"
        }
    }

    public actual val path: String
        get() {
            val getPath: Method = checkSupportForUnixDomainSockets().getMethod("getPath")
            return getPath.invoke(address).toString()
        }

    public actual constructor(path: String) : this(
        checkSupportForUnixDomainSockets()
            .getMethod("of", String::class.java)
            .invoke(null, path) as java.net.SocketAddress
    )

    public actual operator fun component1(): String = path

    public actual fun copy(
        path: String,
    ): UnixSocketAddress = UnixSocketAddress(
        path = path
    )

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UnixSocketAddress

        return address == other.address
    }

    actual override fun hashCode(): Int {
        return address.hashCode()
    }

    public actual override fun toString(): String = address.toString()

    private companion object {
        private val unixDomainSocketAddressClass = try {
            Class.forName(UNIX_DOMAIN_SOCKET_ADDRESS_CLASS)
        } catch (exception: ClassNotFoundException) {
            null
        }

        private fun checkSupportForUnixDomainSockets(): Class<*> {
            return unixDomainSocketAddressClass
                ?: error("Unix domain sockets are unsupported before Java 16.")
        }
    }
}

internal const val UNIX_DOMAIN_SOCKET_ADDRESS_CLASS = "java.net.UnixDomainSocketAddress"
