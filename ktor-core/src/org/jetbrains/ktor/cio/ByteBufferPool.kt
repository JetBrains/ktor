package org.jetbrains.ktor.cio

import java.nio.*

interface ByteBufferPool {
    fun allocate(size: Int): PoolTicket
    fun release(buffer: PoolTicket)
}

interface PoolTicket {
    val buffer: ByteBuffer
}

abstract class ReleasablePoolTicket(private var _buffer: ByteBuffer) : PoolTicket {
    final override val buffer: ByteBuffer
        get() = _buffer.also { if (it === RELEASED) throw IllegalStateException("Buffer already released") }

    fun release() {
        _buffer = RELEASED
    }

    companion object {
        private val RELEASED = ByteBuffer.allocate(0)
    }
}

object NoPool : ByteBufferPool {
    override fun allocate(size: Int): PoolTicket {
        return Ticket(ByteBuffer.allocate(size))
    }

    override fun release(buffer: PoolTicket) {
    }

    private class Ticket(override val buffer: ByteBuffer) : PoolTicket
}


