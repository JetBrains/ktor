package io.ktor.network.sockets

public actual sealed class SocketAddress

public actual class InetSocketAddress actual constructor(
    public actual val hostname: String,
    public actual val port: Int
) : SocketAddress() {
    /**
     * Create a copy of [InetSocketAddress].
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public actual fun copy(hostname: String, port: Int): InetSocketAddress {
        return InetSocketAddress(hostname, port)
    }

    /**
     * The hostname of the socket address.
     *
     * Note that this may trigger a name service reverse lookup.
     */
    public actual operator fun component1(): String {
        return hostname
    }

    /**
     * The port number of the socket address.
     */
    public actual operator fun component2(): Int {
        return port
    }

    public actual override fun equals(other: Any?): Boolean {
        if (other == null || other !is InetSocketAddress) return false
        return other.hostname == hostname && other.port == port
    }

    public actual override fun hashCode(): Int {
        return hostname.hashCode() * 31 + port.hashCode()
    }

    public actual override fun toString(): String {
        return "InetSocketAddress($hostname:$port)"
    }
}

public actual class UnixSocketAddress actual constructor(
    public actual val path: String
) : SocketAddress() {
    /**
     * The path of the socket address.
     */
    public actual operator fun component1(): String {
        return path
    }

    public actual override fun equals(other: Any?): Boolean {
        if (other == null || other !is UnixSocketAddress) return false
        return other.path == path
    }

    public actual override fun hashCode(): Int {
        return path.hashCode()
    }

    public actual override fun toString(): String {
        return "UnixSocketAddress($path)"
    }

    /**
     * Create a copy of [UnixSocketAddress].
     */
    public actual fun copy(path: String): UnixSocketAddress {
        return UnixSocketAddress(path)
    }
}
