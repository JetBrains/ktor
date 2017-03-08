package org.jetbrains.ktor.cio

import kotlinx.coroutines.experimental.*
import java.io.*
import java.nio.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

class ReadChannelFromOutputStream : OutputStream(), ReadChannel {
    private val buffer = ByteBuffer.allocate(8192)
    private val lock = ReentrantLock()
    private val notFull = lock.newCondition()
    private var currentContinuation: Continuation<Int>? = null
    private var currentBuffer: ByteBuffer? = null
    private var closed = false

    suspend override fun read(dst: ByteBuffer): Int {
        check(dst.hasRemaining())
        return suspendCoroutineOrReturn { cont ->
            lock.withLock {
                val count = tryRead(dst)
                if (count > 0)
                    count
                else if (closed)
                    -1
                else {
                    check(currentContinuation == null)
                    check(currentBuffer == null)
                    currentContinuation = cont
                    currentBuffer = dst
                    COROUTINE_SUSPENDED
                }
            }
        }
    }

    private fun tryRead(dst: ByteBuffer): Int {
        if (buffer.position() <= 0) {
            return 0
        } else {
            buffer.flip()
            dst.put(buffer)
            val result = buffer.position()
            buffer.compact()
            notFull.signal()
            return result
        }
    }

    override fun write(b: Int) {
        val (cont, result) = lock.withLock {
            while (!buffer.hasRemaining()) {
                notFull.await()
            }
            buffer.put(b.toByte())
            val cont = currentContinuation?.also { currentContinuation = null }
            val result = currentBuffer?.let {
                currentBuffer = null
                tryRead(it)
            }
            cont to result
        }
        cont?.resume(result!!)
    }

    override fun close() {
        super.close()
        lock.withLock {
            closed = true
            currentContinuation?.also { currentContinuation = null }
        }?.resume(-1)
    }
}

class OutputStreamFromWriteChannel(val channel: WriteChannel, val bufferPool: ByteBufferPool = NoPool) : OutputStream() {
    private val singleByte = bufferPool.allocate(1)
    override fun write(b: Int) = runBlocking(Unconfined) {
        singleByte.buffer.clear()
        singleByte.buffer.put(b.toByte())
        channel.write(singleByte.buffer)
    }

    override fun write(b: ByteArray, off: Int, len: Int) = runBlocking(Unconfined) {
        channel.write(ByteBuffer.wrap(b, off, len))
    }

    override fun close() {
        super.close()
        channel.close()
    }

    override fun flush() = runBlocking(Unconfined) {
        channel.flush()
    }
}

fun WriteChannel.toOutputStream(): OutputStream = OutputStreamFromWriteChannel(this)
