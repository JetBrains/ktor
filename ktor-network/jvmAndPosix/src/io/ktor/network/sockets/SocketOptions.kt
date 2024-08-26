/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

internal const val INFINITE_TIMEOUT_MS = Long.MAX_VALUE

/**
 * Socket options builder
 */
@OptIn(ExperimentalUnsignedTypes::class)
public sealed class SocketOptions(
    protected val customOptions: MutableMap<Any, Any?>
) {
    /**
     * Copy options
     */
    internal abstract fun copy(): SocketOptions

    protected open fun copyCommon(from: SocketOptions) {
        typeOfService = from.typeOfService
        reuseAddress = from.reuseAddress
        reusePort = from.reusePort
    }

    internal fun peer(): PeerSocketOptions {
        return PeerSocketOptions(HashMap(customOptions)).apply {
            copyCommon(this@SocketOptions)
        }
    }

    internal fun acceptor(): AcceptorOptions {
        return AcceptorOptions(HashMap(customOptions)).apply {
            copyCommon(this@SocketOptions)
        }
    }

    private class GeneralSocketOptions(
        customOptions: MutableMap<Any, Any?>
    ) : SocketOptions(customOptions) {
        override fun copy(): GeneralSocketOptions = GeneralSocketOptions(HashMap(customOptions)).apply {
            copyCommon(this@GeneralSocketOptions)
        }
    }

    /**
     * ToS value, [TypeOfService.UNDEFINED] by default, may not work with old JDK (will be silently ignored)
     */
    public var typeOfService: TypeOfService = TypeOfService.UNDEFINED

    /**
     * SO_REUSEADDR option
     */
    public var reuseAddress: Boolean = false

    /**
     * SO_REUSEPORT option, may not work with old JDK (will be silently ignored)
     */
    public var reusePort: Boolean = false

    /**
     * TCP server socket options
     */
    public class AcceptorOptions internal constructor(
        customOptions: MutableMap<Any, Any?>
    ) : SocketOptions(customOptions) {
        /**
         * Represents TCP server socket backlog size. When a client attempts to connect,
         * the request is added to the so called backlog until it will be accepted.
         * Once accept() is invoked, a client socket is removed from the backlog.
         * If the backlog is too small, it may overflow and upcoming requests will be
         * rejected by the underlying TCP implementation (usually with RST frame that
         * usually causes "connection reset by peer" error on the opposite side).
         */
        public var backlogSize: Int = 511

        override fun copy(): AcceptorOptions {
            return AcceptorOptions(HashMap(customOptions)).apply {
                copyCommon(this@AcceptorOptions)
            }
        }
    }

    /**
     * Represents TCP client or UDP socket options
     */
    public open class PeerSocketOptions internal constructor(
        customOptions: MutableMap<Any, Any?>
    ) : SocketOptions(customOptions) {

        /**
         * Socket ougoing buffer size (SO_SNDBUF), `-1` or `0` to make system decide
         */
        public var sendBufferSize: Int = -1

        /**
         * Socket incoming buffer size (SO_RCVBUF), `-1` or `0` to make system decide
         */
        public var receiveBufferSize: Int = -1

        override fun copyCommon(from: SocketOptions) {
            super.copyCommon(from)
            if (from is PeerSocketOptions) {
                sendBufferSize = from.sendBufferSize
                receiveBufferSize = from.receiveBufferSize
            }
        }

        override fun copy(): PeerSocketOptions {
            return PeerSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@PeerSocketOptions)
            }
        }

        internal fun tcp(): TCPClientSocketOptions {
            return TCPClientSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@PeerSocketOptions)
            }
        }

        internal fun udp(): UDPSocketOptions {
            return UDPSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@PeerSocketOptions)
            }
        }
    }

    /**
     * Represents UDP socket options
     */
    public class UDPSocketOptions internal constructor(
        customOptions: MutableMap<Any, Any?>
    ) : PeerSocketOptions(customOptions) {

        /**
         * SO_BROADCAST socket option
         */
        public var broadcast: Boolean = false

        override fun copyCommon(from: SocketOptions) {
            super.copyCommon(from)
            if (from is UDPSocketOptions) {
                broadcast = from.broadcast
            }
        }

        override fun copy(): UDPSocketOptions {
            return UDPSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@UDPSocketOptions)
            }
        }
    }

    /**
     * Represents TCP client socket options
     */
    public class TCPClientSocketOptions internal constructor(
        customOptions: MutableMap<Any, Any?>
    ) : PeerSocketOptions(customOptions) {
        /**
         * TCP_NODELAY socket option, useful to disable Nagle
         */
        public var noDelay: Boolean = true

        /**
         * SO_LINGER option applied at socket close, not recommended to set to 0 however useful for debugging
         * Value of `-1` is the default and means that it is not set and system-dependant
         */
        public var lingerSeconds: Int = -1

        /**
         * SO_KEEPALIVE option is to enable/disable TCP keep-alive
         */
        public var keepAlive: Boolean? = null

        /**
         * Socket timeout (read and write).
         */
        public var socketTimeout: Long = INFINITE_TIMEOUT_MS

        override fun copyCommon(from: SocketOptions) {
            super.copyCommon(from)
            if (from is TCPClientSocketOptions) {
                noDelay = from.noDelay
                lingerSeconds = from.lingerSeconds
                keepAlive = from.keepAlive
            }
        }

        override fun copy(): TCPClientSocketOptions {
            return TCPClientSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@TCPClientSocketOptions)
            }
        }
    }

    public companion object {
        internal fun create(): SocketOptions = GeneralSocketOptions(HashMap())
    }
}
