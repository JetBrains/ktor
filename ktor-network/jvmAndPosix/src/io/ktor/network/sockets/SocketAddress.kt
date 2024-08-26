/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

public expect sealed class SocketAddress

public expect class InetSocketAddress(
    hostname: String,
    port: Int
) : SocketAddress {
    /**
     * The hostname of the socket address.
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public val hostname: String

    /**
     * The port number of the socket address.
     */
    public val port: Int

    /**
     * The hostname of the socket address.
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public operator fun component1(): String

    /**
     * The port number of the socket address.
     */
    public operator fun component2(): Int

    /**
     * Create a copy of [InetSocketAddress].
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public fun copy(hostname: String = this.hostname, port: Int = this.port): InetSocketAddress

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}

public expect class UnixSocketAddress(
    path: String
) : SocketAddress {
    /**
     * The path of the socket address.
     */
    public val path: String

    /**
     * The path of the socket address.
     */
    public operator fun component1(): String

    /**
     * Create a copy of [UnixSocketAddress].
     */
    public fun copy(path: String = this.path): UnixSocketAddress
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String
}
