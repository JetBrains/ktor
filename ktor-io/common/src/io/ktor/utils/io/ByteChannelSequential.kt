package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.internal.*
import io.ktor.utils.io.locks.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlin.math.*

private const val EXPECTED_CAPACITY: Long = 4088L

/**
 * Sequential (non-concurrent) byte channel implementation
 */
@Suppress("OverridingDeprecatedMember", "DEPRECATION")
public abstract class ByteChannelSequentialBase(
    initial: ChunkBuffer,
    override val autoFlush: Boolean,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
) : ByteChannel, ByteReadChannel, ByteWriteChannel, SuspendableReadSession, HasReadSession, HasWriteSession {
    private val _lastReadView: AtomicRef<ChunkBuffer> = atomic(ChunkBuffer.Empty)

    private val _totalBytesRead = atomic(0L)
    private val _totalBytesWritten = atomic(0L)
    private val _availableForRead = atomic(0)
    private val channelSize = atomic(0)

    private val _closed = atomic<CloseElement?>(null)
    private val isCancelled: Boolean get() = _closed.value?.cause != null

    protected var closed: Boolean
        get() = _closed.value != null
        set(_) {
            error("Setting is not allowed for closed")
        }

    protected val writable: BytePacketBuilder = BytePacketBuilder(pool)
    protected val readable: ByteReadPacket = ByteReadPacket(initial, pool)

    private var lastReadAvailable: Int by atomic(0)
    private var lastReadView: ChunkBuffer by atomic(ChunkBuffer.Empty)

    private val slot = AwaitingSlot()

    override val availableForRead: Int get() = _availableForRead.value

    override val availableForWrite: Int
        get() = maxOf(0, EXPECTED_CAPACITY.toInt() - channelSize.value)

    override val isClosedForRead: Boolean
        get() = isCancelled || (closed && channelSize.value == 0)

    override val isClosedForWrite: Boolean
        get() = closed

    override val totalBytesRead: Long
        get() = _totalBytesRead.value

    override val totalBytesWritten: Long get() = _totalBytesWritten.value

    final override var closedCause: Throwable?
        get() = _closed.value?.cause
        set(_) {
            error("Closed cause shouldn't be changed directly")
        }

    @OptIn(InternalAPI::class)
    private val flushMutex = SynchronizedObject()
    private val flushBuffer: BytePacketBuilder = BytePacketBuilder()

    init {
        val count = initial.remainingAll().toInt()
        afterWrite(count)
        _availableForRead.addAndGet(count)
    }

    internal suspend fun awaitAtLeastNBytesAvailableForWrite(count: Int) {
        while (availableForWrite < count && !closed) {
            if (!flushImpl()) {
                slot.sleep { availableForWrite < count && !closed }
            }
        }
    }

    internal suspend fun awaitAtLeastNBytesAvailableForRead(count: Int) {
        while (availableForRead < count && !isClosedForRead) {
            slot.sleep { availableForRead < count && !isClosedForRead }
        }
    }

    override fun flush() {
        flushImpl()
    }

    private fun flushImpl(): Boolean {
        if (writable.isEmpty) {
            slot.resume()
            return false
        }

        flushWrittenBytes()
        slot.resume()
        return true
    }

    /**
     * Send bytes to thread-safe storage.
     *
     * This method is writer-only safe.
     */
    @OptIn(InternalAPI::class)
    private fun flushWrittenBytes() {
        synchronized(flushMutex) {
            val size = writable.size
            val buffer = writable.stealAll()!!
            flushBuffer.writeChunkBuffer(buffer)
            _availableForRead.addAndGet(size)
        }
    }

    /**
     * Take flushed bytes before read.
     *
     * This method is reader-only safe.
     */
    @OptIn(InternalAPI::class)
    protected fun prepareFlushedBytes() {
        synchronized(flushMutex) {
            readable.unsafeAppend(flushBuffer)
        }
    }

    private fun ensureNotClosed() {
        if (closed) {
            throw closedCause ?: ClosedWriteChannelException("Channel $this is already closed")
        }
    }

    private fun ensureNotFailed() {
        closedCause?.let { throw it }
    }

    private fun ensureNotFailed(closeable: BytePacketBuilder) {
        closedCause?.let { cause ->
            closeable.release()
            throw cause
        }
    }

    override suspend fun writeByte(b: Byte) {
        awaitAtLeastNBytesAvailableForWrite(1)
        writable.writeByte(b)
        afterWrite(1)
    }

    override suspend fun writeShort(s: Short) {
        awaitAtLeastNBytesAvailableForWrite(2)
        writable.writeShort(s)
        afterWrite(2)
    }

    override suspend fun writeInt(i: Int) {
        awaitAtLeastNBytesAvailableForWrite(4)
        writable.writeInt(i)
        afterWrite(4)
    }

    override suspend fun writeLong(l: Long) {
        awaitAtLeastNBytesAvailableForWrite(8)
        writable.writeLong(l)
        afterWrite(8)
    }

    override suspend fun writeFloat(f: Float) {
        awaitAtLeastNBytesAvailableForWrite(4)
        writable.writeFloat(f)
        afterWrite(4)
    }

    override suspend fun writeDouble(d: Double) {
        awaitAtLeastNBytesAvailableForWrite(8)
        writable.writeDouble(d)
        afterWrite(8)
    }

    override suspend fun writePacket(packet: ByteReadPacket) {
        awaitAtLeastNBytesAvailableForWrite(1)
        val size = packet.remaining.toInt()
        writable.writePacket(packet)
        afterWrite(size)
    }

    override suspend fun writeFully(src: Buffer) {
        awaitAtLeastNBytesAvailableForWrite(1)
        val count = src.readRemaining
        writable.writeFully(src)
        afterWrite(count)
    }

    override suspend fun writeFully(src: ByteArray, offset: Int, length: Int) {
        var currentIndex = offset
        val endIndex = offset + length

        while (currentIndex < endIndex) {
            awaitAtLeastNBytesAvailableForWrite(1)

            val bytesCount = min(availableForWrite, endIndex - currentIndex)
            writable.writeFully(src, currentIndex, bytesCount)

            currentIndex += bytesCount
            afterWrite(bytesCount)
        }
    }

    override suspend fun writeFully(memory: Memory, startIndex: Int, endIndex: Int) {
        var currentIndex = startIndex

        while (currentIndex < endIndex) {
            awaitAtLeastNBytesAvailableForWrite(1)

            val bytesCount = min(availableForWrite, endIndex - currentIndex)
            writable.writeFully(memory, currentIndex, bytesCount)

            currentIndex += bytesCount
            afterWrite(bytesCount)
        }
    }

    override suspend fun writeAvailable(src: ChunkBuffer): Int {
        val srcRemaining = src.readRemaining
        if (srcRemaining == 0) return 0
        val size = minOf(srcRemaining, availableForWrite)

        return if (size == 0) {
            writeAvailableSuspend(src)
        } else {
            writable.writeFully(src, size)
            afterWrite(size)
            size
        }
    }

    override suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val size = minOf(length, availableForWrite)

        return if (size == 0) {
            writeAvailableSuspend(src, offset, length)
        } else {
            writable.writeFully(src, offset, size)
            afterWrite(size)
            size
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use write { } instead.")
    override suspend fun writeSuspendSession(visitor: suspend WriterSuspendSession.() -> Unit) {
        val session = beginWriteSession()
        visitor(session)
    }

    @Suppress("DEPRECATION")
    override fun beginWriteSession(): WriterSuspendSession {
        return object : WriterSuspendSession {
            override fun request(min: Int): ChunkBuffer? {
                if (availableForWrite == 0) return null
                return writable.prepareWriteHead(min)
            }

            override fun written(n: Int) {
                writable.afterHeadWrite()
                afterWrite(n)
            }

            override fun flush() {
                this@ByteChannelSequentialBase.flush()
            }

            override suspend fun tryAwait(n: Int) {
                if (availableForWrite < n) {
                    awaitAtLeastNBytesAvailableForWrite(n)
                }
            }
        }
    }

    override fun endWriteSession(written: Int) {
        writable.afterHeadWrite()
        afterWrite(written)
    }

    override suspend fun readByte(): Byte {
        return if (readable.isNotEmpty) {
            readable.readByte().also { afterRead(1) }
        } else {
            readByteSlow()
        }
    }

    private fun checkClosed(remaining: Int, closeable: BytePacketBuilder? = null) {
        closedCause?.let {
            closeable?.close()
            throw it
        }
        if (closed && availableForRead < remaining) {
            closeable?.close()
            throw EOFException("$remaining bytes required but EOF reached")
        }
    }

    private suspend fun readByteSlow(): Byte {
        do {
            awaitSuspend(1)

            if (readable.isNotEmpty) return readable.readByte().also { afterRead(1) }
            checkClosed(1)
        } while (true)
    }

    override suspend fun readShort(): Short {
        return if (readable.hasBytes(2)) {
            readable.readShort().also { afterRead(2) }
        } else {
            readShortSlow()
        }
    }

    private suspend fun readShortSlow(): Short {
        awaitSuspend(2)
        val result = readable.readShort()
        afterRead(2)
        return result
    }

    protected fun afterRead(count: Int) {
        addBytesRead(count)
        slot.resume()
    }

    override suspend fun readInt(): Int {
        return if (readable.hasBytes(4)) {
            readable.readInt().also { afterRead(4) }
        } else {
            readIntSlow()
        }
    }

    private suspend fun readIntSlow(): Int {
        awaitSuspend(4)
        val result = readable.readInt()
        afterRead(4)
        return result
    }

    override suspend fun readLong(): Long {
        return if (readable.hasBytes(8)) {
            readable.readLong().also { afterRead(8) }
        } else {
            readLongSlow()
        }
    }

    private suspend fun readLongSlow(): Long {
        awaitSuspend(8)
        val result = readable.readLong()
        afterRead(8)
        return result
    }

    override suspend fun readFloat(): Float = if (readable.hasBytes(4)) {
        readable.readFloat().also { afterRead(4) }
    } else {
        readFloatSlow()
    }

    private suspend fun readFloatSlow(): Float {
        awaitSuspend(4)
        val result = readable.readFloat()
        afterRead(4)
        return result
    }

    override suspend fun readDouble(): Double = if (readable.hasBytes(8)) {
        readable.readDouble().also { afterRead(8) }
    } else {
        readDoubleSlow()
    }

    private suspend fun readDoubleSlow(): Double {
        awaitSuspend(8)
        val result = readable.readDouble()
        afterRead(8)
        return result
    }

    override suspend fun readRemaining(limit: Long): ByteReadPacket {
        ensureNotFailed()

        val builder = BytePacketBuilder()

        val size = minOf(limit, readable.remaining)
        builder.writePacket(readable, size)
        afterRead(size.toInt())

        val newLimit = limit - builder.size
        return if (newLimit == 0L || isClosedForRead) {
            ensureNotFailed(builder)
            builder.build()
        } else {
            readRemainingSuspend(builder, limit)
        }
    }

    private suspend fun readRemainingSuspend(builder: BytePacketBuilder, limit: Long): ByteReadPacket {
        while (builder.size < limit) {
            val partLimit = minOf(limit - builder.size, readable.remaining)
            builder.writePacket(readable, partLimit)
            afterRead(partLimit.toInt())
            ensureNotFailed(builder)

            if (isClosedForRead || builder.size == limit.toInt()) {
                break
            }

            awaitSuspend(1)
        }

        ensureNotFailed(builder)
        return builder.build()
    }

    override suspend fun readPacket(size: Int): ByteReadPacket {
        checkClosed(size)

        val builder = BytePacketBuilder()

        var remaining = size
        val partSize = minOf(remaining.toLong(), readable.remaining).toInt()
        remaining -= partSize
        builder.writePacket(readable, partSize)
        afterRead(partSize)
        checkClosed(remaining, builder)

        return if (remaining > 0) {
            readPacketSuspend(builder, remaining)
        } else builder.build()
    }

    private suspend fun readPacketSuspend(builder: BytePacketBuilder, size: Int): ByteReadPacket {
        var remaining = size
        while (remaining > 0) {
            val partSize = minOf(remaining.toLong(), readable.remaining).toInt()
            remaining -= partSize
            builder.writePacket(readable, partSize)
            afterRead(partSize)
            checkClosed(remaining, builder)

            if (remaining > 0) {
                awaitSuspend(1)
            }
        }

        checkClosed(remaining, builder)
        return builder.build()
    }

    protected fun readAvailableClosed(): Int {
        closedCause?.let { throw it }

        if (availableForRead > 0) {
            prepareFlushedBytes()
        }

        return -1
    }

    override suspend fun readAvailable(dst: ChunkBuffer): Int = readAvailable(dst as Buffer)

    internal suspend fun readAvailable(dst: Buffer): Int {
        closedCause?.let { throw it }
        if (closed && availableForRead == 0) return -1

        if (dst.writeRemaining == 0) return 0

        if (availableForRead == 0) {
            awaitSuspend(1)
        }

        if (!readable.canRead()) {
            prepareFlushedBytes()
        }

        val size = minOf(dst.writeRemaining.toLong(), readable.remaining).toInt()
        readable.readFully(dst, size)
        afterRead(size)
        return size
    }

    override suspend fun readFully(dst: ChunkBuffer, n: Int) {
        readFully(dst as Buffer, n)
    }

    private suspend fun readFully(dst: Buffer, n: Int) {
        require(n <= dst.writeRemaining) { "Not enough space in the destination buffer to write $n bytes" }
        require(n >= 0) { "n shouldn't be negative" }

        return when {
            closedCause != null -> throw closedCause!!
            readable.remaining >= n -> readable.readFully(dst, n).also { afterRead(n) }
            closed -> throw EOFException(
                "Channel is closed and not enough bytes available: required $n but $availableForRead available"
            )

            else -> readFullySuspend(dst, n)
        }
    }

    private suspend fun readFullySuspend(dst: Buffer, n: Int) {
        awaitSuspend(n)
        return readFully(dst, n)
    }

    override suspend fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        closedCause?.let { throw it }
        if (closed && availableForRead == 0) return -1

        if (length == 0) return 0

        if (availableForRead == 0) {
            awaitSuspend(1)
        }

        if (!readable.canRead()) {
            prepareFlushedBytes()
        }

        val size = minOf(length.toLong(), readable.remaining).toInt()
        readable.readFully(dst, offset, size)
        afterRead(size)
        return size
    }

    override suspend fun readFully(dst: ByteArray, offset: Int, length: Int) {
        val rc = readAvailable(dst, offset, length)
        if (rc == length) return
        if (rc == -1) throw EOFException("Unexpected end of stream")

        return readFullySuspend(dst, offset + rc, length - rc)
    }

    private suspend fun readFullySuspend(dst: ByteArray, offset: Int, length: Int) {
        var written = 0

        while (written < length) {
            val rc = readAvailable(dst, offset + written, length - written)
            if (rc == -1) throw EOFException("Unexpected end of stream")
            written += rc
        }
    }

    override suspend fun readBoolean(): Boolean {
        return if (readable.canRead()) {
            (readable.readByte() == 1.toByte()).also { afterRead(1) }
        } else {
            readBooleanSlow()
        }
    }

    private suspend fun readBooleanSlow(): Boolean {
        awaitSuspend(1)
        checkClosed(1)
        return readBoolean()
    }

    private fun completeReading() {
        val remaining = lastReadView.readRemaining
        val delta = lastReadAvailable - remaining
        if (lastReadView !== Buffer.Empty) {
            readable.completeReadHead(lastReadView)
        }
        if (delta > 0) {
            afterRead(delta)
        }
        lastReadAvailable = 0
        lastReadView = ChunkBuffer.Empty
    }

    override suspend fun await(atLeast: Int): Boolean {
        require(atLeast >= 0) { "atLeast parameter shouldn't be negative: $atLeast" }
        require(atLeast <= EXPECTED_CAPACITY) {
            "atLeast parameter shouldn't be larger than max buffer size of $EXPECTED_CAPACITY: $atLeast"
        }

        completeReading()

        if (atLeast == 0) return !isClosedForRead
        if (readable.remaining >= atLeast) return true

        return awaitSuspend(atLeast)
    }

    internal suspend fun awaitInternalAtLeast1(): Boolean = if (readable.isNotEmpty) {
        true
    } else {
        awaitSuspend(1)
    }

    protected suspend fun awaitSuspend(atLeast: Int): Boolean {
        require(atLeast >= 0)

        awaitAtLeastNBytesAvailableForRead(atLeast)
        prepareFlushedBytes()

        closedCause?.let { throw it }
        return !isClosedForRead && availableForRead >= atLeast
    }

    override fun discard(n: Int): Int {
        closedCause?.let { throw it }

        if (n == 0) {
            return 0
        }

        return readable.discard(n).also {
            afterRead(n)
            requestNextView(1)
        }
    }

    override fun request(atLeast: Int): ChunkBuffer? {
        closedCause?.let { throw it }

        completeReading()

        return requestNextView(atLeast)
    }

    private fun requestNextView(atLeast: Int): ChunkBuffer? {
        if (readable.isEmpty) {
            prepareFlushedBytes()
        }

        val view = readable.prepareReadHead(atLeast)

        if (view == null) {
            lastReadView = ChunkBuffer.Empty
            lastReadAvailable = 0
        } else {
            lastReadView = view
            lastReadAvailable = view.readRemaining
        }

        return view
    }

    override suspend fun discard(max: Long): Long {
        val discarded = readable.discard(max)
        afterRead(discarded.toInt())

        return if (discarded == max || isClosedForRead) {
            ensureNotFailed()
            return discarded
        } else {
            discardSuspend(max, discarded)
        }
    }

    private suspend fun discardSuspend(max: Long, discarded0: Long): Long {
        var discarded = discarded0

        do {
            if (!await(1)) break
            val count = readable.discard(max - discarded)
            afterRead(count.toInt())
            discarded += count
        } while (discarded < max && !isClosedForRead)

        ensureNotFailed()

        return discarded
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use read instead.")
    override fun readSession(consumer: ReadSession.() -> Unit) {
        try {
            consumer(this)
        } finally {
            completeReading()
        }
    }

    override fun startReadSession(): SuspendableReadSession = this

    override fun endReadSession() {
        completeReading()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use read instead.")
    override suspend fun readSuspendableSession(consumer: suspend SuspendableReadSession.() -> Unit) {
        try {
            consumer(this)
        } finally {
            completeReading()
        }
    }

    override suspend fun <A : Appendable> readUTF8LineTo(out: A, limit: Int): Boolean {
        if (isClosedForRead) {
            val cause = closedCause
            if (cause != null) {
                throw cause
            }

            return false
        }

        return decodeUTF8LineLoopSuspend(out, limit, { size ->
            if (await(size)) {
                readable
            } else {
                null
            }
        }) { afterRead(it) }
    }

    override suspend fun readUTF8Line(limit: Int): String? {
        val builder = StringBuilder()
        if (!readUTF8LineTo(builder, limit)) {
            return null
        }

        return builder.toString()
    }

    override fun cancel(cause: Throwable?): Boolean {
        if (closedCause != null || closed) {
            return false
        }

        return close(cause ?: CancellationException("Channel cancelled"))
    }

    override fun close(cause: Throwable?): Boolean {
        val closeElement = if (cause == null) CLOSED_SUCCESS else CloseElement(cause)
        if (!_closed.compareAndSet(null, closeElement)) return false

        if (cause != null) {
            readable.release()
            writable.release()
            flushBuffer.release()
        } else {
            flush()
            writable.release()
        }

        slot.cancel(cause)
        return true
    }

    internal fun transferTo(dst: ByteChannelSequentialBase, limit: Long): Long {
        val size = readable.remaining
        return if (size <= limit) {
            dst.writable.writePacket(readable)
            dst.afterWrite(size.toInt())
            afterRead(size.toInt())
            size
        } else {
            0
        }
    }

    private suspend fun writeAvailableSuspend(src: ChunkBuffer): Int {
        awaitAtLeastNBytesAvailableForWrite(1)
        return writeAvailable(src)
    }

    private suspend fun writeAvailableSuspend(src: ByteArray, offset: Int, length: Int): Int {
        awaitAtLeastNBytesAvailableForWrite(1)
        return writeAvailable(src, offset, length)
    }

    protected fun afterWrite(count: Int) {
        addBytesWritten(count)

        if (closed) {
            writable.release()
            ensureNotClosed()
        }
        if (autoFlush || availableForWrite == 0) {
            flush()
        }
    }

    override suspend fun awaitFreeSpace() {
        flush()
        awaitAtLeastNBytesAvailableForWrite(1)
        ensureNotClosed()
    }

    /**
     * Suspend until the channel has bytes to read or gets closed. Throws exception if the channel was closed with an error.
     */
    override suspend fun awaitContent() {
        await(1)
    }

    final override suspend fun peekTo(
        destination: Memory,
        destinationOffset: Long,
        offset: Long,
        min: Long,
        max: Long
    ): Long {
        var bytesCopied = 0L

        @Suppress("DEPRECATION")
        readSuspendableSession {
            val desiredSize = (min + offset).coerceAtMost(EXPECTED_CAPACITY).toInt()

            await(desiredSize)

            val buffer = request(1) ?: ChunkBuffer.Empty
            if (buffer.readRemaining > offset) {
                bytesCopied = minOf(buffer.readRemaining.toLong() - offset, max, destination.size - destinationOffset)
                buffer.memory.copyTo(destination, offset, bytesCopied, destinationOffset)
            }
        }

        return bytesCopied
    }

    private fun addBytesRead(count: Int) {
        require(count >= 0) { "Can't read negative amount of bytes: $count" }

        channelSize.minusAssign(count)
        _totalBytesRead.addAndGet(count.toLong())
        _availableForRead.minusAssign(count)

        check(channelSize.value >= 0) { "Readable bytes count is negative: $availableForRead, $count in $this" }
        check(availableForRead >= 0) { "Readable bytes count is negative: $availableForRead, $count in $this" }
    }

    private fun addBytesWritten(count: Int) {
        require(count >= 0) { "Can't write negative amount of bytes: $count" }

        channelSize.plusAssign(count)
        _totalBytesWritten.addAndGet(count.toLong())

        check(channelSize.value >= 0) { "Readable bytes count is negative: ${channelSize.value}, $count in $this" }
    }
}
