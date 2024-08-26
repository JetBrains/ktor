/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*

internal const val MAX_DATAGRAM_SIZE = 65535

/**
 * UDP datagram with [packet] content targeted to [address]
 * @property packet content
 * @property address to send to
 */
public class Datagram(
    public val packet: Source,
    public val address: SocketAddress
) {
    init {
        require(packet.remaining <= MAX_DATAGRAM_SIZE) {
            "Datagram size limit exceeded: ${packet.remaining} of possible $MAX_DATAGRAM_SIZE"
        }
    }
}

/**
 * A channel for sending datagrams
 */
public interface DatagramWriteChannel {
    /**
     * Datagram outgoing channel.
     */
    public val outgoing: SendChannel<Datagram>

    /**
     * Send datagram.
     */
    public suspend fun send(datagram: Datagram) {
        outgoing.send(datagram)
    }
}

/**
 * A channel for receiving datagrams
 */
public interface DatagramReadChannel {
    /**
     * Incoming datagrams channel
     */
    public val incoming: ReceiveChannel<Datagram>

    /**
     * Receive a datagram.
     */
    public suspend fun receive(): Datagram = incoming.receive()
}

/**
 * A channel for sending and receiving datagrams
 */
public interface DatagramReadWriteChannel : DatagramReadChannel, DatagramWriteChannel

/**
 * Represents a bound datagram socket
 */
public interface BoundDatagramSocket : ASocket, ABoundSocket, DatagramReadWriteChannel

/**
 * Represents a connected datagram socket.
 */
public interface ConnectedDatagramSocket :
    ASocket, ABoundSocket, AConnectedSocket, DatagramReadWriteChannel
