package io.ktor.client.engine.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.collections.*
import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.locks.*
import kotlinx.coroutines.sync.*

private val LOG = KtorSimpleLogger("io.ktor.client.engine.cio.ConnectionFactory")


@OptIn(InternalAPI::class)
internal class ConnectionFactory(
    private val selector: SelectorManager,
    private val connectionsLimit: Int,
    private val addressConnectionsLimit: Int,
    private val keepAliveTime: Long = 30_000, // Default keep-alive time in milliseconds
    private val maxPoolSize: Int = 100 // Maximum pool size
) : SynchronizedObject() {

    private val limit = Semaphore(connectionsLimit)
    private val addressLimit = ConcurrentMap<SocketAddress, Semaphore>()
    private val connectionPool = mutableMapOf<SocketAddress, MutableList<PooledConnection>>()

    suspend fun connect(
        address: InetSocketAddress,
        configuration: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket {

        LOG.trace { "Attempting to connect to address: $address" }
        // Try to get a connection from the pool
        val pooledConnection = getPooledConnection(address)
        if (pooledConnection != null) {
            LOG.trace { "Reusing pooled connection for address: $address" }
            return pooledConnection.socket // Return the socket from the pooled connection
        }

        // If no pooled connection is available, create a new one
        limit.acquire()
        LOG.trace { "No pooled connection available, creating new connection to address: $address" }
        return try {
            val addressSemaphore = addressLimit.computeIfAbsent(address) { Semaphore(addressConnectionsLimit) }
            addressSemaphore.acquire()

            try {
                val socket = aSocket(selector).tcp().connect(address, configuration)
                LOG.trace { "Successfully connected to address: $address" }
                socket
            } catch (cause: Throwable) {
                LOG.error("Failed to connect to address: $address", cause)
                addressSemaphore.release()
                throw cause
            }
        } catch (cause: Throwable) {
            limit.release()
            throw cause
        }
    }

    fun release(socket: Socket) {
        LOG.trace { "Releasing connection for address: ${socket.remoteAddress}" }

        if (socket.isClosed) {
            LOG.warn("Attempted to release a closed connection for address: ${socket.remoteAddress}")
            return
        }

        val pooledConnection = PooledConnection(socket, GMTDate().timestamp)
        synchronized(this) {
            val pool = connectionPool.getOrPut(socket.remoteAddress) { mutableListOf() }
            pool.add(pooledConnection)

            // If pool size exceeds maxPoolSize, remove and close excess connections
            while (pool.size > maxPoolSize) {
                LOG.trace { "Pool size exceeded maxPoolSize, removing oldest connection" }
                pool.removeAt(0).close()
            }

            limit.release() // Release the semaphore to allow new connections
            addressLimit[socket.remoteAddress]?.release() // Release the address semaphore
        }
        LOG.trace { "Connection released for address: ${socket.remoteAddress}" }
    }

    private fun getPooledConnection(address: InetSocketAddress): PooledConnection? {
        LOG.trace { "Checking for pooled connection for address: $address" }
        val connections = connectionPool[address] ?: return null
        val currentTime = GMTDate().timestamp

        synchronized(this) {
            connections.removeAll {
                if (currentTime - it.lastUsed > keepAliveTime) {
                    LOG.trace { "Removing expired connection for address: $address" }
                    it.close()
                    return@removeAll true
                }
                false
            }

            if (connections.isNotEmpty()) {
                val connection = connections.removeAt(0)
                LOG.trace { "Pooled connection found for address: $address" }
                return connection
            }
        }
        LOG.trace { "No pooled connection available for address: $address" }
        return null
    }
}

private class PooledConnection(
    val socket: Socket,
    var lastUsed: Long
) {
    fun close() {
        LOG.trace { "Closing socket for address: ${socket.remoteAddress}" }
        socket.close()
    }
}
