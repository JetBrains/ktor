/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.utils.io.core.*
import java.net.*
import java.nio.*
import java.nio.ByteOrder
import java.nio.channels.*
import java.nio.channels.spi.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.concurrent.*
import kotlin.io.use
import kotlin.text.toByteArray

/**
 * This HTTP generator produces huge amount of requests however it doesn't validate responses and doesn't measure
 * any time characteristics.
 *
 * It provides two modes: when [highPressure] is `false` then it parses response stream, counts status codes and
 * enqueues new requests (in other words it means that it is waiting for responses but uses pipelining).
 *
 * In [highPressure] mode the load generator only produces requests as much as possible
 * and discards all server responses. In this mode it generates too high pressure so server could fail or get stuck
 * due to long long tasks queue. If server could manage so much requests then
 * RPS is much higher (up to 10x higher) in this mode
 * but load generator provides absolutely no diagnostics.
 */
public class HighLoadHttpGenerator(
    public val host: String,
    port: Int,
    public val numberOfConnections: Int,
    public val queueSize: Int,
    public val highPressure: Boolean,
    builder: RequestResponseBuilder.() -> Unit
) {

    public constructor(
        url: String,
        host: String,
        port: Int,
        numberConnections: Int,
        queueSize: Int,
        highPressure: Boolean
    ) : this(
        host,
        port,
        numberConnections,
        queueSize,
        highPressure,
        {
            requestLine(HttpMethod.Get, url, "HTTP/1.1")
            headerLine(HttpHeaders.Host, "$host:$port")
            headerLine(HttpHeaders.Accept, "*/*")
            emptyLine()
        }
    )

    private val remote = InetSocketAddress(host, port)
    private val request = RequestResponseBuilder().apply(builder).build()

    private val requestByteBuffer = ByteBuffer.allocateDirect(request.remaining.toInt())!!.apply {
        request.copy().readFully(this)
        clear()
    }

    private val count = AtomicLong(0)
    private val codeCounts = Array(1000) { AtomicLong(0) }
    private val readErrors = AtomicLong()
    private val writeErrors = AtomicLong()
    private val connectErrors = AtomicLong()

    @Volatile
    private var cancelled = false

    @Volatile
    private var shutdown = false

    private enum class ParseState {
        EOL,
        HTTP,
        SPACE,
        CODE
    }

    private inner class ClientState(val channel: SocketChannel) {
        private val current = requestByteBuffer.duplicate()
        var remaining = 0
            private set

        private var parseState = ParseState.HTTP
        private var tokenSize = 0
        private var code = 0

        var key: SelectionKey? = null
        var writePending: Boolean = false
        var readPending: Boolean = false
        var currentOps = 0

        private fun calcOps(): Int {
            var ops = 0
            if (writePending) {
                ops = ops or SelectionKey.OP_WRITE
            }
            if (readPending) {
                ops = ops or SelectionKey.OP_READ
            }

            return ops
        }

        public fun interest(selector: Selector) {
            val ops = calcOps()
            val key = key

            try {
                if (key == null) {
                    this.key = channel.register(selector, ops, this)
                    currentOps = ops
                } else if (currentOps != ops) {
                    key.interestOps(ops)
                    currentOps = ops
                }
            } catch (t: Throwable) {
                close()
            }
        }

        public fun send(qty: Int = 1) {
            require(qty > 0)
            if (!shutdown) {
                remaining += qty
                if (!current.hasRemaining()) {
                    current.clear()
                }
            }
        }

        public fun close() {
            key?.cancel()
            key = null
            readPending = false
            writePending = false

            try {
                channel.close()
            } catch (ignore: Throwable) {
            }
        }

        internal tailrec fun doWrite(): Boolean {
            if (remaining == 0) return true
            val hp = highPressure

            channel.write(current)
            if (!current.hasRemaining()) {
                count.incrementAndGet()
                return when {
                    shutdown -> {
                        remaining = 0
                        true
                    }
                    hp -> {
                        current.clear()
                        doWrite()
                    }
                    --remaining > 0 -> {
                        current.clear()
                        doWrite()
                    }
                    else -> true
                }
            }

            return false
        }

        internal fun doRead(bb: ByteBuffer): Int {
            bb.clear()
            val rc = channel.read(bb)
            if (rc == -1) {
                close()
                return -1
            }

            if (rc == 0) return 0

            if (!highPressure) {
                bb.flip()
                parseLoop(bb)
            }

            return 1
        }

        private fun parseLoop(bb: ByteBuffer) {
            while (bb.hasRemaining()) {
                when (parseState) {
                    ParseState.EOL -> findEol(bb)
                    ParseState.HTTP -> findHttp(bb)
                    ParseState.SPACE -> skipSpaces(bb)
                    ParseState.CODE -> parseCode(bb)
                }
            }
        }

        private fun findEol(bb: ByteBuffer) {
            val position = bb.position()
            val limit = bb.limit()

            for (idx in position until limit) {
                if (bb[idx] == N) {
                    parseState = ParseState.HTTP
                    tokenSize = 0
                    bb.position(idx + 1)
                    return
                }
            }

            bb.position(limit)
        }

        private fun findHttp(bb: ByteBuffer) {
            val position = bb.position()
            val limit = bb.limit()

            if (tokenSize == 0 && limit - position >= 8) {
                if (bb.getLong(position) == HTTP11Long) {
                    parseState = ParseState.SPACE
                    tokenSize = 0
                    bb.position(position + 8)
                    return
                } else {
                    parseState = ParseState.EOL
                    return
                }
            }

            return findHttpSlow(bb, position, limit)
        }

        private fun findHttpSlow(bb: ByteBuffer, position: Int, limit: Int) {
            val http = HTTP11
            val offset = tokenSize - position

            for (idx in position until limit) {
                val b = bb[idx]

                if (b == S) {
                    parseState = ParseState.SPACE
                    tokenSize = 0
                    bb.position(idx + 1)
                    return
                }
                if (b == http[idx + offset]) {
                    if (++tokenSize > HTTP1_length) {
                        parseState = ParseState.EOL
                        bb.position(idx + 1)
                        return
                    }
                } else {
                    parseState = ParseState.EOL
                    bb.position(idx)
                    return
                }
            }

            bb.position(limit)
        }

        private fun skipSpaces(bb: ByteBuffer) {
            val position = bb.position()
            val limit = bb.limit()

            if (limit - position >= 4) {
                val i = bb.getInt(position)
                if (i == HTTP_200_SPACE_Int || i == HTTP_200_R_Int) {
                    gotStatus(200)
                    parseState = ParseState.EOL
                    bb.position(position + 3)
                    return
                }
            }

            return skipSpacesSlow(bb, position, limit)
        }

        private fun skipSpacesSlow(bb: ByteBuffer, position: Int, limit: Int) {
            for (idx in position until limit) {
                val b = bb[idx]

                if (b == N) {
                    parseState = ParseState.HTTP
                    bb.position(idx + 1)
                    return
                }
                if (b == S) {
                    if (++tokenSize > 10) {
                        parseState = ParseState.EOL
                        bb.position(idx + 1)
                        return
                    } else {
                        continue
                    }
                }
                if (b == 0x32.toByte() && limit - idx >= 4) {
                    val i = bb.getInt(idx)
                    if (i == HTTP_200_SPACE_Int || i == HTTP_200_R_Int) {
                        gotStatus(200)
                        parseState = ParseState.EOL
                        bb.position(idx + 3)
                        return
                    }
                }

                val n = b - 0x30
                if (n in 0..9) {
                    parseState = ParseState.CODE
                    code = n
                    tokenSize = 1
                    bb.position(idx + 1)
                    return
                }

                parseState = ParseState.EOL
                bb.position(idx + 1)
                return
            }

            bb.position(limit)
        }

        private fun parseCode(bb: ByteBuffer) {
            var code = code

            while (bb.hasRemaining()) {
                val b = bb.get()
                if (b == S || b == N) {
                    // found code
                    if (code in 100..999) {
                        gotStatus(code)
                    }

                    parseState = ParseState.EOL
                    return
                }

                val n = b - 0x30
                if (n < 0 || n > 9) {
                    parseState = ParseState.EOL
                    return
                }

                if (++tokenSize > 3) {
                    parseState = ParseState.EOL
                    return
                }

                code = code * 10 + n
            }

            this.code = code
        }

        private fun gotStatus(code: Int) {
            codeCounts[code].incrementAndGet()
            send(1)
        }

        /*
        private inline fun scan(bb: ByteBuffer, predicate: (Byte) -> Boolean): Boolean {
            var rem = bb.remaining()
            val mask = 0xff.toLong() shl 24

            while (rem >= 8) {
                var l = bb.getLong()
                rem -= 8

                for (i in 0..7) {
                    val b = ((l and mask) ushr 24).toByte()
                    l = l shl 8

                    if (predicate(b)) {
                        bb.position(bb.position() - 7 + i) // 7 because we eat one
                        return true
                    }
                }
            }

            while (rem > 0) {
                val b = bb.get()
                rem--

                if (predicate(b)) return true
            }

            return false
        }
         */
    }

    public fun shutdown() {
        shutdown = true
    }

    public fun stop() {
        cancelled = true
    }

    public fun mainLoop() {
        val provider = SelectorProvider.provider()!!
        val selector = provider.openSelector()!!

        selector.use {
            var connectionsCount = 0
            var writeReady = ArrayList<ClientState>(numberOfConnections)
            var writeReadyTmp = ArrayList<ClientState>(numberOfConnections)
            val readReady = ArrayList<ClientState>(numberOfConnections)
            val pending = ArrayList<ClientState>(numberOfConnections * 2)
            val bb = ByteBuffer.allocateDirect(65536)!!
            bb.order(ByteOrder.BIG_ENDIAN)

            var connectFailureInRowCount = 0
            while (!cancelled && connectFailureInRowCount < 100) {
                if (connectionsCount < numberOfConnections) {
                    val ch = provider.openSocketChannel()!!
                    ch.configureBlocking(false)

                    try {
                        val client = ClientState(ch)
                        client.send(queueSize)

                        if (ch.connect(remote)) {
                            writeReady.add(client)
                        } else {
                            client.key = ch.register(selector, SelectionKey.OP_CONNECT, client)
                            client.currentOps = SelectionKey.OP_CONNECT
                        }
                        connectionsCount++
                        connectFailureInRowCount = 0
                    } catch (t: Throwable) {
                        ch.close()
                        connectErrors.incrementAndGet()
                        connectFailureInRowCount++
//                            println("connect() or register() failed: $t")
                    }
                }

                for (idx in 0 until writeReady.size) {
                    if (cancelled) break
                    val c = writeReady[idx]
                    if (!c.channel.isConnected) continue

                    try {
                        if (!c.doWrite()) {
                            c.writePending = true
                            readReady.add(c)
                            pending.add(c)
                        } else {
                            readReady.add(c)
                            if (c.writePending) {
                                c.writePending = false
                                pending.add(c)
                            }

                            if (c.remaining > 0) {
                                writeReadyTmp.add(c)
                            }
                        }
                    } catch (t: Throwable) {
//                            println("write() failed: $t")
                        writeErrors.incrementAndGet()
                        c.close()
                        connectionsCount--
                    }
                }
                writeReady.clear()
                val tmp = writeReadyTmp
                writeReadyTmp = writeReady
                writeReady = tmp

                for (idx in 0 until readReady.size) {
                    if (cancelled) break
                    val c = readReady[idx]
                    if (!c.channel.isConnected) continue

                    try {
                        while (true) {
                            val rc = c.doRead(bb)
                            if (rc > 0) continue

                            if (rc == -1) {
                                connectionsCount--
                                c.close()
                            } else {
                                c.readPending = true
                                pending.add(c)

                                if (c.remaining > 0) {
                                    writeReady.add(c)
                                }
                            }

                            break
                        }
                    } catch (t: Throwable) {
//                            println("read() failed: $t")
                        readErrors.incrementAndGet()
                        c.close()
                        connectionsCount--
                    }
                }
                readReady.clear()

                for (idx in 0 until pending.size) {
                    if (cancelled) break
                    val c = pending[idx]
                    c.interest(selector)
                }
                pending.clear()

                val hasKeys = selector.keys().isNotEmpty()

                val selectedCount = when {
                    cancelled -> 0
                    !hasKeys -> 0
                    connectionsCount < numberOfConnections -> selector.selectNow()
                    writeReady.isNotEmpty() -> selector.selectNow()
                    readReady.isNotEmpty() -> selector.selectNow()
                    else -> selector.select(500)
                }

                if (selectedCount > 0) {
                    val iter = selector.selectedKeys().iterator()
                    while (iter.hasNext()) {
                        val key = iter.next()!!
                        val client = key.attachment() as ClientState

                        if (!client.channel.isOpen) {
                            client.close()
                        } else if (client.channel.isConnectionPending) {
                            try {
                                check(client.channel.finishConnect())
                                writeReady.add(client)
                            } catch (t: Throwable) {
                                client.close()
                                connectionsCount--
//                                    println("finishConnect() failed: $t")
                                connectErrors.incrementAndGet()
                            }
                        } else {
                            val readyOps = key.readyOps()
                            if (readyOps and SelectionKey.OP_READ != 0) {
                                client.readPending = false
                                readReady.add(client)
                            }
                            if (readyOps and SelectionKey.OP_WRITE != 0) {
                                client.writePending = false
                                writeReady.add(client)
                            }
                        }

                        iter.remove()
                        client.interest(selector)
                    }
                }
            }

            selector.keys().forEach {
                it.cancel()
                try {
                    it.channel().close()
                } catch (ignore: Throwable) {
                }
            }
        }
    }

    private fun stat(): String = StringBuilder().apply {
        appendLine("count: ${count.get()}")
        appendLine("errors: read ${readErrors.get()}, write ${writeErrors.get()}, connect: ${connectErrors.get()}")
        if (codeCounts.any { it.get() > 0 }) {
            appendLine("statuses:")
            codeCounts.forEachIndexed { idx, a ->
                val cnt = a.get()
                if (cnt > 0) {
                    appendLine("  $idx    $cnt")
                }
            }
        }
    }.toString()

    public companion object {
        private val HTTP11 = "HTTP/1.1".toByteArray()
        private const val HTTP11Long = 0x485454502f312e31L
        private const val HTTP1_length = 8

        private const val HTTP_200_SPACE_Int = 0x32303020
        private const val HTTP_200_R_Int = 0x3230300d

        private const val N = '\n'.code.toByte()
        private const val S = 0x20.toByte()

        public fun doRun(
            url: String,
            host: String,
            port: Int,
            numberOfThreads: Int,
            connectionsPerThread: Int,
            queueSize: Int,
            highPressure: Boolean,
            gracefulMillis: Long,
            timeMillis: Long
        ) {
            val generator = HighLoadHttpGenerator(
                url,
                host,
                port,
                connectionsPerThread,
                queueSize,
                highPressure
            )
            doRun(
                generator,
                numberOfThreads,
                gracefulMillis,
                timeMillis
            )
        }

        public fun doRun(
            host: String,
            port: Int,
            numberOfThreads: Int,
            connectionsPerThread: Int,
            queueSize: Int,
            highPressure: Boolean,
            gracefulMillis: Long,
            timeMillis: Long,
            builder: RequestResponseBuilder.() -> Unit
        ) {
            val generator = HighLoadHttpGenerator(
                host,
                port,
                connectionsPerThread,
                queueSize,
                highPressure,
                builder
            )
            doRun(
                generator,
                numberOfThreads,
                gracefulMillis,
                timeMillis
            )
        }

        private fun doRun(
            loadGenerator: HighLoadHttpGenerator,
            numberOfThreads: Int,
            gracefulMillis: Long,
            timeMillis: Long
        ) {
            println("Running...")
            val threads = (1..numberOfThreads).map {
                thread {
                    loadGenerator.mainLoop()
                }
            }
            val joiner = thread(start = false) {
                threads.forEach {
                    it.join(gracefulMillis)
                }
            }

            try {
                Thread.sleep(timeMillis)
                println("Shutting down...")
                loadGenerator.shutdown()
                joiner.start()
                joiner.join(gracefulMillis)
            } finally {
                println("Termination...")
                loadGenerator.stop()
                threads.forEach { it.interrupt() }
                joiner.join()
                println("Terminated.")
                println(loadGenerator.stat())
            }
        }

        @JvmStatic
        public fun main(args: Array<String>) {
            val debug = false

            val url = URL("http://localhost:8081/")
            val connections = 4000
            val queue = 100
            val time = 20
            val highPressure = false

            val numberCpu = if (debug) 1 else Runtime.getRuntime().availableProcessors()
            val pathAndQuery = buildString {
                append(url.path)
                if (!url.query.isNullOrEmpty()) {
                    append('?')
                    append(url.query)
                }
            }

            val manager = HighLoadHttpGenerator(
                pathAndQuery,
                url.host,
                if (url.port == -1) 80 else url.port,
                connections / numberCpu,
                queue,
                highPressure
            )
            val threads = (1..numberCpu).map {
                thread(start = false) {
                    manager.mainLoop()
                }
            }

            threads.forEach { it.start() }

            if (debug) {
                Thread.sleep(Long.MAX_VALUE)
            } else {
                TimeUnit.SECONDS.sleep(time.toLong())
            }

            manager.shutdown()
            Thread.sleep(1000)
            manager.stop()

            threads.forEach { it.join(1000) }
            if (threads.any { it.isAlive }) {
                println("There are threads get stuck")
            }

            println(manager.stat())
        }
    }
}
