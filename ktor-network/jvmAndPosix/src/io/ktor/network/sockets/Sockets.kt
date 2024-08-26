/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*

/**
 * Base type for all async sockets
 */
public interface ASocket : Closeable, DisposableHandle {
    /**
     * Represents a socket lifetime, completes at socket closure
     */
    public val socketContext: Job

    public override fun dispose() {
        try {
            close()
        } catch (ignore: Throwable) {
        }
    }
}

/**
 * Check if the socket is closed
 */
public val ASocket.isClosed: Boolean get() = socketContext.isCompleted

/**
 * Await until socket close
 */
public suspend fun ASocket.awaitClosed() {
    socketContext.join()

    @OptIn(InternalCoroutinesApi::class)
    if (socketContext.isCancelled) throw socketContext.getCancellationException()
}

/**
 * Represent a connected socket
 */
public interface AConnectedSocket {
    /**
     * Remote socket address. Could throw an exception if the peer is not yet connected or already disconnected.
     */
    public val remoteAddress: SocketAddress
}

/**
 * Represents a bound socket
 */
public interface ABoundSocket {
    /**
     * Local socket address. Could throw an exception if no address bound yet.
     */
    public val localAddress: SocketAddress
}

/**
 * Represents a socket source, for example server socket
 */
public interface Acceptable<out S : ASocket> : ASocket {
    /**
     * Suspends until a connection is available and returns it or throws if something
     * goes wrong.
     *
     * @return accepted socket
     * @throws IOException
     */
    public suspend fun accept(): S
}

/**
 * Represent a readable socket
 */
public interface AReadable {
    /**
     * Attach [channel] for reading so incoming bytes appears in the attached channel.
     * Only one channel could be attached
     * @return a job that does supply data
     */

    public fun attachForReading(channel: ByteChannel): WriterJob
}

/**
 * Represents a writable socket
 */
public interface AWritable {
    /**
     * Attach [channel] for writing so bytes written to the attached channel will be transmitted
     * Only one channel could be attached
     * @return a job that does transmit data from the channel
     */

    public fun attachForWriting(channel: ByteChannel): ReaderJob
}

/**
 * Represents both readable and writable socket
 */
public interface ReadWriteSocket : ASocket, AReadable, AWritable

/**
 * Open a read channel, could be done only once
 */

public fun AReadable.openReadChannel(): ByteReadChannel = ByteChannel(false).also { attachForReading(it) }

/**
 * Open a write channel, could be opened only once
 * @param autoFlush whether returned channel do flush for every write operation
 */

public fun AWritable.openWriteChannel(autoFlush: Boolean = false): ByteWriteChannel =
    ByteChannel(autoFlush).also { attachForWriting(it) }

/**
 * Represents a connected socket
 */
public interface Socket : ReadWriteSocket, ABoundSocket, AConnectedSocket, CoroutineScope

/**
 * Represents a server bound socket ready for accepting connections
 */
public interface ServerSocket : ASocket, ABoundSocket, Acceptable<Socket>

public expect class SocketTimeoutException(message: String) : IOException

/**
 * Represents a connected socket with its input and output
 */
public class Connection(
    public val socket: Socket,
    public val input: ByteReadChannel,
    public val output: ByteWriteChannel
)

/**
 * Opens socket input and output channels and returns connection object
 */
public fun Socket.connection(): Connection = Connection(this, openReadChannel(), openWriteChannel())
