package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.lang.Double.*
import java.lang.Float.*
import java.nio.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.math.*

internal const val DEFAULT_CLOSE_MESSAGE: String = "Byte channel was closed"
private const val BYTE_BUFFER_CAPACITY: Int = 4088
private val DEFAULT_CLOSE_CAUSE = ClosedWriteChannelException(DEFAULT_CLOSE_MESSAGE)
private val DEFAULT_CANCELLATION_EXCEPTION: CancellationException = CancellationException("Channel has been cancelled")

// implementation for ByteChannel
@Suppress("DEPRECATION", "OverridingDeprecatedMember")
internal open class ByteBufferChannel(
    override val autoFlush: Boolean,
    private val pool: ObjectPool<ReadWriteBufferState.Initial> = BufferObjectPool,
    internal val reservedSize: Int = RESERVED_SIZE
) : ByteChannel, ByteReadChannel, ByteWriteChannel, LookAheadSuspendSession, HasReadSession, HasWriteSession {

    constructor(content: ByteBuffer) : this(false, BufferObjectNoPool, 0) {
        _state.value = ReadWriteBufferState.Initial(content.slice(), 0).apply {
            capacity.resetForRead()
        }.startWriting()
        restoreStateAfterWrite()
        close()
        tryTerminate()
    }

    private val _state: AtomicRef<ReadWriteBufferState> = atomic(ReadWriteBufferState.IdleEmpty)

    private val state: ReadWriteBufferState
        get() = _state.value

    private val _closed: AtomicRef<ClosedElement?> = atomic(null)

    private var closed: ClosedElement?
        get() = _closed.value
        set(value) {
            _closed.value = value
        }

    @Volatile
    private var joining: JoiningState? = null

    private val _readOp: AtomicRef<Continuation<Boolean>?> = atomic(null)

    private var readOp: Continuation<Boolean>?
        get() = _readOp.value
        set(value) {
            _readOp.value = value
        }

    private val _writeOp: AtomicRef<Continuation<Unit>?> = atomic(null)

    private var writeOp: Continuation<Unit>?
        get() = _writeOp.value
        set(value) {
            _writeOp.value = value
        }

    private var readPosition = 0
    private var writePosition = 0

    @Volatile
    private var attachedJob: Job? = null

    internal fun currentState(): ReadWriteBufferState = state

    internal fun getJoining(): JoiningState? = joining

    @Deprecated(IO_DEPRECATION_MESSAGE)
    @OptIn(InternalCoroutinesApi::class)
    override fun attachJob(job: Job) {
        // TODO actually it looks like one-direction attachChild API
        attachedJob?.cancel()
        attachedJob = job
        job.invokeOnCompletion(onCancelling = true) { cause ->
            attachedJob = null
            cause ?: return@invokeOnCompletion
            cancel(cause.unwrapCancellationException())
        }
    }

    override val availableForRead: Int
        get() = state.capacity.availableForRead

    override val availableForWrite: Int
        get() = state.capacity.availableForWrite

    override val isClosedForRead: Boolean
        get() = state === ReadWriteBufferState.Terminated && closed != null

    override val isClosedForWrite: Boolean
        get() = closed != null

    @Volatile
    override var totalBytesRead: Long = 0L
        internal set

    @Volatile
    override var totalBytesWritten: Long = 0L
        internal set

    override val closedCause: Throwable?
        get() = closed?.cause

    override fun close(cause: Throwable?): Boolean {
        if (closed != null) {
            return false
        }

        val newClosed = if (cause == null) {
            ClosedElement.EmptyCause
        } else {
            ClosedElement(cause)
        }

        state.capacity.flush()
        if (!_closed.compareAndSet(null, newClosed)) {
            return false
        }

        state.capacity.flush()
        if (state.capacity.isEmpty() || cause != null) {
            tryTerminate()
        }

        resumeClosed(cause)

        if (state === ReadWriteBufferState.Terminated) {
            joining?.let { ensureClosedJoined(it) }
        }

        if (cause != null) {
            attachedJob?.cancel()

            readSuspendContinuationCache.close(cause)
            writeSuspendContinuationCache.close(cause)
            return true
        }

        // don't cancel job

        // any further attempt to suspend should be resumed immediately
        // with exception for write
        writeSuspendContinuationCache.close(DEFAULT_CLOSE_CAUSE)

        // and with computed result for read
        readSuspendContinuationCache.close(state.capacity.flush())
        return true
    }

    override fun cancel(cause: Throwable?): Boolean {
        return close(cause ?: DEFAULT_CANCELLATION_EXCEPTION)
    }

    private fun flushImpl(minWriteSize: Int) {
        joining?.delegatedTo?.flush()

        val avw: Int
        val avr: Int

        while (true) {
            val currentState = state
            if (currentState === ReadWriteBufferState.Terminated) {
                return
            }

            currentState.capacity.flush()
            if (currentState === state) {
                avw = currentState.capacity.availableForWrite
                avr = currentState.capacity.availableForRead
                break
            }
        }

        if (avr >= 1) {
            resumeReadOp()
        }

        val joining = joining
        if (avw >= minWriteSize && (joining == null || state === ReadWriteBufferState.Terminated)) {
            resumeWriteOp()
        }
    }

    override fun flush() {
        flushImpl(minWriteSize = 1)
    }

    internal fun prepareWriteBuffer(buffer: ByteBuffer, lockedSpace: Int) {
        buffer.prepareBuffer(writePosition, lockedSpace)
    }

    private fun ByteBuffer.prepareBuffer(position: Int, available: Int) {
        require(position >= 0)
        require(available >= 0)

        val bufferLimit = capacity() - reservedSize
        val virtualLimit = position + available

        limit(virtualLimit.coerceAtMost(bufferLimit))
        position(position)
    }

    internal fun setupStateForWrite(): ByteBuffer? {
        writeOp?.let { existing ->
            throw IllegalStateException("Write operation is already in progress: $existing")
        }

        var allocatedState: ReadWriteBufferState.Initial? = null
        lateinit var old: ReadWriteBufferState

        val newState = _state.updateAndGet { state ->
            old = state
            when {
                joining != null -> {
                    allocatedState?.let { releaseBuffer(it) }
                    return null
                }

                closed != null -> {
                    allocatedState?.let { releaseBuffer(it) }
                    rethrowClosed(closed!!.sendException)
                }

                state === ReadWriteBufferState.IdleEmpty -> {
                    val allocated = allocatedState ?: newBuffer().also { allocatedState = it }
                    allocated.startWriting()
                }

                state === ReadWriteBufferState.Terminated -> {
                    allocatedState?.let { releaseBuffer(it) }
                    if (joining != null) return null
                    rethrowClosed(closed!!.sendException)
                }

                else -> {
                    state.startWriting()
                }
            }
        }

        if (closed != null) {
            restoreStateAfterWrite()
            tryTerminate()

            rethrowClosed(closed!!.sendException)
        }

        val buffer = newState.writeBuffer

        allocatedState?.let { allocated ->
            if (old !== ReadWriteBufferState.IdleEmpty) {
                releaseBuffer(allocated)
            }
        }
        return buffer.apply {
            prepareBuffer(writePosition, newState.capacity.availableForWrite)
        }
    }

    internal fun restoreStateAfterWrite() {
        var toRelease: ReadWriteBufferState.IdleNonEmpty? = null

        val newState = _state.updateAndGet {
            val writeStopped = it.stopWriting()
            if (writeStopped is ReadWriteBufferState.IdleNonEmpty && writeStopped.capacity.isEmpty()) {
                toRelease = writeStopped
                ReadWriteBufferState.IdleEmpty
            } else {
                writeStopped
            }
        }

        if (newState === ReadWriteBufferState.IdleEmpty) {
            toRelease?.let { releaseBuffer(it.initial) }
        }
    }

    private fun setupStateForRead(): ByteBuffer? {
        val newState = _state.updateAndGet { state ->
            when (state) {
                ReadWriteBufferState.Terminated,
                ReadWriteBufferState.IdleEmpty -> {
                    val cause = closed?.cause ?: return null
                    rethrowClosed(cause)
                }

                else -> {
                    closed?.cause?.let { rethrowClosed(it) }
                    if (state.capacity.availableForRead == 0) return null
                    state.startReading()
                }
            }
        }

        return newState.readBuffer.apply {
            prepareBuffer(readPosition, newState.capacity.availableForRead)
        }
    }

    private fun restoreStateAfterRead() {
        var toRelease: ReadWriteBufferState.IdleNonEmpty? = null

        val newState = _state.updateAndGet { state ->
            toRelease?.let {
                it.capacity.resetForWrite()
                resumeWriteOp()
                toRelease = null
            }

            val readStopped = state.stopReading()

            if (readStopped is ReadWriteBufferState.IdleNonEmpty &&
                this.state === state && readStopped.capacity.tryLockForRelease()
            ) {
                toRelease = readStopped
                ReadWriteBufferState.IdleEmpty
            } else {
                readStopped
            }
        }

        if (newState === ReadWriteBufferState.IdleEmpty) {
            toRelease?.let { releaseBuffer(it.initial) }
            resumeWriteOp()
            return
        }

        if (newState is ReadWriteBufferState.IdleNonEmpty &&
            newState.capacity.isEmpty() &&
            newState.capacity.tryLockForRelease() &&
            _state.compareAndSet(newState, ReadWriteBufferState.IdleEmpty)
        ) {
            newState.capacity.resetForWrite()
            releaseBuffer(newState.initial)
            resumeWriteOp()
        }
    }

    private fun setupDelegateTo(delegate: ByteBufferChannel, delegateClose: Boolean): JoiningState {
        require(this !== delegate)

        val joined = JoiningState(delegate, delegateClose)
        this.joining = joined

        val alreadyClosed = closed
        if (alreadyClosed == null) {
            flush()
            return joined
        }

        when {
            alreadyClosed.cause != null -> delegate.close(alreadyClosed.cause)
            delegateClose && state === ReadWriteBufferState.Terminated -> delegate.close()
            else -> delegate.flush()
        }

        return joined
    }

    private fun tryCompleteJoining(joined: JoiningState): Boolean {
        if (!tryReleaseBuffer(true)) return false
        ensureClosedJoined(joined)

        resumeReadOp { IllegalStateException("Joining is in progress") }
        resumeWriteOp() // here we don't resume it with exception because it should resume and delegate writing

        return true
    }

    internal fun tryTerminate(): Boolean {
        if (closed == null || !tryReleaseBuffer(false)) {
            return false
        }

        joining?.let { ensureClosedJoined(it) }

        resumeReadOp()
        resumeWriteOp()
        return true
    }

    private fun tryReleaseBuffer(forceTermination: Boolean): Boolean {
        var toRelease: ReadWriteBufferState.Initial? = null

        _state.update { state ->
            val closed = closed

            toRelease?.let { buffer ->
                toRelease = null
                if (closed?.cause == null) buffer.capacity.resetForWrite()
                resumeWriteOp()
            }

            when {
                state === ReadWriteBufferState.Terminated -> return true
                state === ReadWriteBufferState.IdleEmpty -> ReadWriteBufferState.Terminated
                closed != null && state is ReadWriteBufferState.IdleNonEmpty &&
                    (state.capacity.tryLockForRelease() || closed.cause != null) -> {
                    if (closed.cause != null) state.capacity.forceLockForRelease()
                    toRelease = state.initial
                    ReadWriteBufferState.Terminated
                }

                forceTermination && state is ReadWriteBufferState.IdleNonEmpty &&
                    state.capacity.tryLockForRelease() -> {
                    toRelease = state.initial
                    ReadWriteBufferState.Terminated
                }

                else -> return false
            }
        }

        toRelease?.let { buffer ->
            if (state === ReadWriteBufferState.Terminated) {
                releaseBuffer(buffer)
            }
        }

        return true
    }

    private fun ByteBuffer.carryIndex(idx: Int): Int =
        if (idx >= capacity() - reservedSize) idx - (capacity() - reservedSize) else idx

    private inline fun writing(block: ByteBufferChannel.(ByteBuffer, RingBufferCapacity) -> Unit) {
        val current = joining?.let { resolveDelegation(this, it) } ?: this
        val buffer = current.setupStateForWrite() ?: return
        val capacity = current.state.capacity
        val before = current.totalBytesWritten

        try {
            current.closed?.let { rethrowClosed(it.sendException) }
            block(current, buffer, capacity)
        } finally {
            if (capacity.isFull() || current.autoFlush) current.flush()
            if (current !== this) {
                totalBytesWritten += current.totalBytesWritten - before
            }
            current.restoreStateAfterWrite()
            current.tryTerminate()
        }
    }

    private inline fun reading(block: ByteBuffer.(RingBufferCapacity) -> Boolean): Boolean {
        val buffer = setupStateForRead() ?: return false
        val capacity = state.capacity
        try {
            if (capacity.availableForRead == 0) return false

            return block(buffer, capacity)
        } finally {
            restoreStateAfterRead()
            tryTerminate()
        }
    }

    private fun readAsMuchAsPossible(dst: ByteBuffer): Int {
        var consumed = 0

        reading { state ->
            val buffer = this
            val bufferLimit = buffer.capacity() - reservedSize

            while (true) {
                val dstRemaining = dst.remaining()
                if (dstRemaining == 0) break

                val position = readPosition
                val bufferRemaining = bufferLimit - position

                val part = state.tryReadAtMost(minOf(bufferRemaining, dstRemaining))
                if (part == 0) break

                buffer.limit(position + part)
                buffer.position(position)
                dst.put(buffer)

                bytesRead(state, part)
                consumed += part
            }

            false
        }

        return consumed
    }

    private fun readAsMuchAsPossible(
        dst: io.ktor.utils.io.core.Buffer,
        consumed: Int = 0,
        max: Int = dst.writeRemaining
    ): Int {
        var currentConsumed = consumed
        var currentMax = max

        do {
            var part = 0
            val count = reading {
                val dstSize = dst.writeRemaining
                part = it.tryReadAtMost(minOf(remaining(), dstSize, currentMax))
                if (part <= 0) {
                    return@reading false
                }

                if (dstSize < remaining()) {
                    limit(position() + dstSize)
                }

                dst.writeFully(this)

                bytesRead(it, part)
                return@reading true
            }

            currentConsumed += part
            currentMax -= part
        } while (count && dst.canWrite() && state.capacity.availableForRead > 0)

        return currentConsumed
    }

    private fun readAsMuchAsPossible(dst: ByteArray, offset: Int, length: Int): Int {
        var consumed = 0

        reading { state ->
            val buffer = this
            val bufferLimit = buffer.capacity() - reservedSize

            while (true) {
                val lengthRemaining = length - consumed
                if (lengthRemaining == 0) break
                val position = readPosition
                val bufferRemaining = bufferLimit - position

                val part = state.tryReadAtMost(minOf(bufferRemaining, lengthRemaining))
                if (part == 0) break

                buffer.limit(position + part)
                buffer.position(position)
                buffer.get(dst, offset + consumed, part)

                bytesRead(state, part)
                consumed += part
            }

            false
        }

        return consumed
    }

    final override suspend fun readFully(dst: ByteArray, offset: Int, length: Int) {
        val consumed = readAsMuchAsPossible(dst, offset, length)

        if (consumed < length) {
            readFullySuspend(dst, offset + consumed, length - consumed)
        }
    }

    final override suspend fun readFully(dst: ByteBuffer): Int {
        val rc = readAsMuchAsPossible(dst)
        if (!dst.hasRemaining()) return rc

        return readFullySuspend(dst, rc)
    }

    private suspend fun readFullySuspend(dst: ByteBuffer, rc0: Int): Int {
        var copied = rc0

        while (dst.hasRemaining()) {
            if (!readSuspend(1)) {
                throw ClosedReceiveChannelException("Unexpected EOF: expected ${dst.remaining()} more bytes")
            }

            copied += readAsMuchAsPossible(dst)
        }

        return copied
    }

    override suspend fun readFully(dst: ChunkBuffer, n: Int) {
        val rc = readAsMuchAsPossible(dst, max = n)
        if (rc == n) {
            return
        }

        readFullySuspend(dst, n - rc)
    }

    private suspend fun readFullySuspend(dst: ChunkBuffer, n: Int) {
        var copied = 0

        while (dst.canWrite() && copied < n) {
            if (!readSuspend(1)) {
                throw ClosedReceiveChannelException("Unexpected EOF: expected ${n - copied} more bytes")
            }

            copied += readAsMuchAsPossible(dst, max = n - copied)
        }
    }

    private suspend fun readFullySuspend(dst: ByteArray, offset: Int, length: Int) {
        var currentOffset = offset
        var currentLength = length

        var consumed = 0
        do {
            if (!readSuspend(1)) {
                throw ClosedReceiveChannelException("Unexpected EOF: expected $currentLength more bytes")
            }
            currentOffset += consumed
            currentLength -= consumed

            consumed = readAsMuchAsPossible(dst, currentOffset, currentLength)
        } while (consumed < currentLength)
    }

    override fun readAvailable(min: Int, block: (ByteBuffer) -> Unit): Int {
        require(min > 0) { "min should be positive" }
        require(min <= BYTE_BUFFER_CAPACITY) { "Min($min) shouldn't be greater than $BYTE_BUFFER_CAPACITY" }

        var result = 0
        val read = reading { state ->
            val locked = state.tryReadAtLeast(min)

            if (locked <= 0 || locked < min) {
                return@reading false
            }

            // here we have locked all available for read bytes
            // however we don't know how many bytes will be actually read
            // so later we have to return (locked - actuallyRead) bytes back

            // it is important to lock bytes to fail concurrent tryLockForRelease
            // once we have locked some bytes, tryLockForRelease will fail so it is safe to use buffer

            val position = position()
            val limit = limit()
            block(this)
            check(limit == limit()) { "Buffer limit shouldn't be modified." }

            result = position() - position
            check(result >= 0) { "Position shouldn't been moved backwards." }

            bytesRead(state, result)

            if (result < locked) {
                state.completeWrite(locked - result) // return back extra bytes (see note above)
                // we use completeWrite in spite of that it is read block
                // we don't need to resume read as we are already in read block

                // flush returned bytes back for subsequent reads
                state.flush()
            }

            return@reading true
        }

        if (!read) return -1
        return result
    }

    override suspend fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        val consumed = readAsMuchAsPossible(dst, offset, length)

        return when {
            consumed == 0 && closed != null -> {
                if (state.capacity.flush()) {
                    readAsMuchAsPossible(dst, offset, length)
                } else {
                    -1
                }
            }

            consumed > 0 || length == 0 -> consumed
            else -> readAvailableSuspend(dst, offset, length)
        }
    }

    override suspend fun readAvailable(dst: ByteBuffer): Int {
        val consumed = readAsMuchAsPossible(dst)

        return when {
            consumed == 0 && closed != null -> {
                if (state.capacity.flush()) {
                    readAsMuchAsPossible(dst)
                } else {
                    -1
                }
            }

            consumed > 0 || !dst.hasRemaining() -> consumed
            else -> readAvailableSuspend(dst)
        }
    }

    override suspend fun readAvailable(dst: ChunkBuffer): Int {
        val consumed = readAsMuchAsPossible(dst)

        return when {
            consumed == 0 && closed != null -> {
                if (state.capacity.flush()) {
                    readAsMuchAsPossible(dst)
                } else {
                    -1
                }
            }

            consumed > 0 || !dst.canWrite() -> consumed
            else -> readAvailableSuspend(dst)
        }
    }

    private suspend fun readAvailableSuspend(dst: ByteArray, offset: Int, length: Int): Int {
        if (!readSuspend(1)) {
            return -1
        }

        return readAvailable(dst, offset, length)
    }

    private suspend fun readAvailableSuspend(dst: ByteBuffer): Int {
        if (!readSuspend(1)) {
            return -1
        }

        return readAvailable(dst)
    }

    private suspend fun readAvailableSuspend(dst: ChunkBuffer): Int {
        if (!readSuspend(1)) {
            return -1
        }

        return readAvailable(dst)
    }

    override suspend fun readPacket(size: Int): ByteReadPacket {
        closed?.cause?.let { rethrowClosed(it) }

        if (size == 0) return ByteReadPacket.Empty

        val builder = BytePacketBuilder()
        val buffer = BufferPool.borrow()
        var remaining = size

        try {
            while (remaining > 0) {
                buffer.clear()
                if (buffer.remaining() > remaining) {
                    buffer.limit(remaining)
                }

                val rc = readAsMuchAsPossible(buffer)
                if (rc == 0) break

                buffer.flip()
                builder.writeFully(buffer)

                remaining -= rc
            }
        } catch (cause: Throwable) {
            BufferPool.recycle(buffer)
            builder.release()
            throw cause
        }

        return if (remaining == 0) {
            BufferPool.recycle(buffer)
            builder.build()
        } else {
            readPacketSuspend(remaining, builder, buffer)
        }
    }

    private suspend fun readPacketSuspend(size: Int, builder: BytePacketBuilder, buffer: ByteBuffer): ByteReadPacket {
        var remaining = size

        try {
            while (remaining > 0) {
                buffer.clear()
                if (buffer.remaining() > remaining) {
                    buffer.limit(remaining)
                }

                val rc = readFully(buffer)

                buffer.flip()
                builder.writeFully(buffer)

                remaining -= rc
            }

            return builder.build()
        } catch (cause: Throwable) {
            builder.release()
            throw cause
        } finally {
            BufferPool.recycle(buffer)
        }
    }

    final override suspend fun readBoolean(): Boolean {
        return readByte() != 0.toByte()
    }

    final override suspend fun readByte(): Byte {
        return readPrimitive(1, ByteBuffer::get)
    }

    final override suspend fun readShort(): Short {
        return readPrimitive(2, ByteBuffer::getShort)
    }

    final override suspend fun readInt(): Int {
        return readPrimitive(4, ByteBuffer::getInt)
    }

    final override suspend fun readLong(): Long {
        return readPrimitive(8, ByteBuffer::getLong)
    }

    final override suspend fun readFloat(): Float {
        return intBitsToFloat(readPrimitive(4, ByteBuffer::getInt))
    }

    final override suspend fun readDouble(): Double {
        return longBitsToDouble(readPrimitive(8, ByteBuffer::getLong))
    }

    private suspend inline fun <T : Number> readPrimitive(
        size: Int,
        getter: ByteBuffer.() -> T
    ): T {
        while (true) {
            lateinit var result: T

            val rc = reading {
                if (!it.tryReadExact(size)) {
                    return@reading false
                }

                if (remaining() < size) rollBytes(size)
                result = getter()
                bytesRead(it, size)
                return@reading true
            }

            if (rc) {
                return result
            }
            if (!readSuspend(size)) {
                throw ClosedReceiveChannelException("EOF while $size bytes expected")
            }
        }
    }

    private fun ByteBuffer.rollBytes(n: Int) {
        val remaining = remaining()

        limit(position() + n)
        for (i in 0 until n - remaining) {
            put(capacity() + ReservedLongIndex + i, get(i))
        }
    }

    private fun ByteBuffer.carry() {
        val base = capacity() - reservedSize
        for (i in base until position()) {
            put(i - base, get(i))
        }
    }

    internal fun bytesWrittenFromSession(buffer: ByteBuffer, capacity: RingBufferCapacity, count: Int) {
        buffer.bytesWritten(capacity, count)
    }

    private fun ByteBuffer.bytesWritten(capacity: RingBufferCapacity, count: Int) {
        require(count >= 0)

        writePosition = carryIndex(writePosition + count)
        capacity.completeWrite(count)
        totalBytesWritten += count
    }

    private fun ByteBuffer.bytesRead(capacity: RingBufferCapacity, count: Int) {
        require(count >= 0)

        readPosition = carryIndex(readPosition + count)
        capacity.completeRead(count)
        totalBytesRead += count
        resumeWriteOp()
    }

    internal fun resolveChannelInstance(): ByteBufferChannel {
        return joining?.let { resolveDelegation(this, it) } ?: this
    }

    private fun resolveDelegation(current: ByteBufferChannel, joining: JoiningState): ByteBufferChannel? {
        var currentChannel: ByteBufferChannel = current
        var currentJoining: JoiningState = joining

        while (true) {
            if (currentChannel.state !== ReadWriteBufferState.Terminated) {
                return null
            }

            val joinedTo = currentJoining.delegatedTo
            currentJoining = joinedTo.joining ?: return joinedTo
            currentChannel = joinedTo
        }
    }

    private suspend inline fun delegateSuspend(joined: JoiningState, block: ByteBufferChannel.() -> Unit) {
        while (true) {
            if (state === ReadWriteBufferState.Terminated) return block(joined.delegatedTo)
            writeSuspend(1)
        }
    }

    override suspend fun writeByte(b: Byte) {
        writePrimitive(1, { writeByte(b) }, { put(b) })
    }

    override suspend fun writeShort(s: Short) {
        writePrimitive(2, { writeShort(s) }, { putShort(s) })
    }

    override suspend fun writeInt(i: Int) {
        writePrimitive(4, { writeInt(i) }, { putInt(i) })
    }

    override suspend fun writeLong(l: Long) {
        writePrimitive(8, { writeLong(l) }, { putLong(l) })
    }

    override suspend fun writeDouble(d: Double) {
        writeLong(doubleToRawLongBits(d))
    }

    override suspend fun writeFloat(f: Float) {
        writeInt(floatToRawIntBits(f))
    }

    private suspend inline fun writePrimitive(
        size: Int,
        channelWriter: ByteBufferChannel.() -> Unit,
        bufferWriter: ByteBuffer.() -> Unit
    ) {
        joining?.let { resolveDelegation(this, it)?.let { return it.channelWriter() } }

        val buffer = setupStateForWrite() ?: return delegatePrimitive(channelWriter)
        val capacity = state.capacity

        if (!buffer.tryWritePrimitive(size, capacity, bufferWriter)) {
            buffer.writeSuspendPrimitive(size, capacity, channelWriter, bufferWriter)
        }
    }

    private inline fun ByteBuffer.tryWritePrimitive(
        size: Int,
        capacity: RingBufferCapacity,
        writer: ByteBuffer.() -> Unit
    ): Boolean {
        if (!capacity.tryWriteExact(size)) {
            return false
        }
        prepareWriteBuffer(this, size)
        doWritePrimitive(size, this, capacity, writer)
        return true
    }

    private inline fun doWritePrimitive(
        size: Int,
        buffer: ByteBuffer,
        capacity: RingBufferCapacity,
        writer: ByteBuffer.() -> Unit
    ) {
        buffer.apply {
            if (remaining() < size) {
                limit(capacity())
                writer()
                carry()
            } else {
                writer()
            }

            bytesWritten(capacity, size)
        }

        if (capacity.isFull() || autoFlush) {
            flush()
        }
        restoreStateAfterWrite()
        tryTerminate()
    }

    private suspend inline fun ByteBuffer.writeSuspendPrimitive(
        size: Int,
        capacity: RingBufferCapacity,
        channelWriter: ByteBufferChannel.() -> Unit,
        bufferWriter: ByteBuffer.() -> Unit
    ) {
        do {
            try {
                writeSuspend(size)
            } catch (cause: Throwable) {
                restoreStateAfterWrite()
                tryTerminate()
                throw cause
            }

            if (joining != null) {
                restoreStateAfterWrite()
                delegatePrimitive(channelWriter)
                return
            }
        } while (!tryWritePrimitive(size, capacity, bufferWriter))
    }

    private suspend inline fun delegatePrimitive(channelWriter: ByteBufferChannel.() -> Unit) {
        val joined = joining!!
        if (state === ReadWriteBufferState.Terminated) {
            joined.delegatedTo.channelWriter()
            return
        }

        delegateSuspend(joined) {
            channelWriter()
        }
    }

    override suspend fun awaitFreeSpace() {
        writeSuspend(1)
    }

    override suspend fun writeAvailable(src: ByteBuffer): Int {
        joining?.let { resolveDelegation(this, it)?.let { return it.writeAvailable(src) } }

        val copied = writeAsMuchAsPossible(src)
        if (copied > 0) return copied

        joining?.let { resolveDelegation(this, it)?.let { return it.writeAvailableSuspend(src) } }
        return writeAvailableSuspend(src)
    }

    override suspend fun writeAvailable(src: ChunkBuffer): Int {
        joining?.let { resolveDelegation(this, it)?.let { return it.writeAvailable(src) } }

        val copied = writeAsMuchAsPossible(src)
        if (copied > 0) return copied

        joining?.let { resolveDelegation(this, it)?.let { return it.writeAvailableSuspend(src) } }
        return writeAvailableSuspend(src)
    }

    private suspend fun writeAvailableSuspend(src: ByteBuffer): Int {
        writeSuspend(1) // here we don't need to restoreStateAfterWrite as write copy loop doesn't hold state

        joining?.let { resolveDelegation(this, it)?.let { return it.writeAvailableSuspend(src) } }

        return writeAvailable(src)
    }

    private suspend fun writeAvailableSuspend(src: ChunkBuffer): Int {
        writeSuspend(1)

        joining?.let { resolveDelegation(this, it)?.let { return it.writeAvailableSuspend(src) } }

        return writeAvailable(src)
    }

    override suspend fun writeFully(src: ByteBuffer) {
        joining?.let { resolveDelegation(this, it)?.let { return it.writeFully(src) } }

        writeAsMuchAsPossible(src)
        if (!src.hasRemaining()) return

        return writeFullySuspend(src)
    }

    override suspend fun writeFully(src: io.ktor.utils.io.core.Buffer) {
        writeAsMuchAsPossible(src)

        if (!src.canRead()) {
            return
        }

        writeFullySuspend(src)
    }

    override suspend fun writeFully(memory: Memory, startIndex: Int, endIndex: Int) {
        val slice = memory.slice(startIndex, endIndex - startIndex)
        writeFully(slice.buffer)
    }

    private suspend fun writeFullySuspend(src: ByteBuffer) {
        while (src.hasRemaining()) {
            tryWriteSuspend(1)

            joining?.let { resolveDelegation(this, it)?.let { return it.writeFully(src) } }

            writeAsMuchAsPossible(src)
        }
    }

    private suspend fun writeFullySuspend(src: io.ktor.utils.io.core.Buffer) {
        while (src.canRead()) {
            tryWriteSuspend(1)

            joining?.let { resolveDelegation(this, it)?.let { return it.writeFully(src) } }

            writeAsMuchAsPossible(src)
        }
    }

    private suspend fun awaitClose() {
        if (closed != null) return
        val joined = joining

        if (joined == null) {
            check(closed != null) { "Only works for joined." }
            return
        }

        joined.awaitClose()
    }

    internal suspend fun joinFrom(src: ByteBufferChannel, delegateClose: Boolean) {
        if (src.closed != null && src.state === ReadWriteBufferState.Terminated) {
            if (delegateClose) close(src.closed!!.cause)
            return
        }
        closed?.let { closed ->
            if (src.closed == null) rethrowClosed(closed.sendException)
            return
        }

        val joined = src.setupDelegateTo(this, delegateClose)
        if (src.tryCompleteJoining(joined)) {
            src.awaitClose()
            return
        }

        joinFromSuspend(src, delegateClose, joined)
    }

    private suspend fun joinFromSuspend(src: ByteBufferChannel, delegateClose: Boolean, joined: JoiningState) {
        copyDirect(src, Long.MAX_VALUE, joined)

        if (delegateClose && src.isClosedForRead) {
            close()
            return
        }

        flush()
        src.awaitClose()
    }

    // prevent from boxing
    private var partSize = 0
    private var copied: Long = 0L

    internal suspend fun copyDirect(src: ByteBufferChannel, limit: Long, joined: JoiningState?): Long {
        assert(limit > 0)

        if (src.isClosedForRead) {
            if (joined != null) {
                check(src.tryCompleteJoining(joined))
            }
            if (src.closedCause != null) {
                close(src.closedCause)
            }
            return 0L
        }

        if (joined != null && src.tryCompleteJoining(joined)) {
            return 0L
        }

        val autoFlush = autoFlush

        try {
            copied = 0
            while (copied < limit) {
                writing { dstBuffer, state ->
                    while (copied < limit) {
                        var avWBefore = state.availableForWrite
                        if (avWBefore == 0) {
                            tryWriteSuspend(1)
                            if (joining != null) break
                            avWBefore = state.availableForWrite
                        }

                        dstBuffer.prepareBuffer(writePosition, avWBefore)

                        partSize = 0

                        src.reading { srcState ->
                            val srcBuffer = this

                            val rem = minOf(
                                srcBuffer.remaining().toLong(),
                                dstBuffer.remaining().toLong(),
                                limit - copied
                            ).toInt()
                            val n = state.tryWriteAtMost(rem)

                            if (n <= 0) {
                                return@reading true
                            }

                            if (!srcState.tryReadExact(n)) {
                                // TODO: Why do we have assertion error here?
                                throw AssertionError()
                            }

                            srcBuffer.limit(srcBuffer.position() + n)

                            dstBuffer.put(srcBuffer)
                            partSize = n

                            with(src) {
                                srcBuffer.bytesRead(srcState, n)
                            }

                            return@reading true
                        }

                        if (partSize <= 0) {
                            break
                        }

                        dstBuffer.bytesWritten(state, partSize)
                        copied += partSize

                        if (avWBefore - partSize == 0 || autoFlush) {
                            flush()
                        }
                    }
                }

                if (joined != null) {
                    // force flush src to read-up all the bytes
                    if (src.tryCompleteJoining(joined)) {
                        break
                    }

                    // force flush src to read-up all the bytes
                    if (src.state.capacity.flush()) {
                        src.resumeWriteOp()
                        continue
                    }
                }

                if (copied >= limit) break

                flush()

                if (src.availableForRead == 0) {
                    if (src.readSuspendImpl(1)) {
                        if (joined != null && src.tryCompleteJoining(joined)) break
                    } else {
                        if (joined == null || src.tryCompleteJoining(joined)) break
                    }
                }

                if (joining != null) {
                    tryWriteSuspend(1)
                }
            }

            if (autoFlush) {
                flush()
            }

            return copied
        } catch (cause: Throwable) {
            close(cause)
            throw cause
        }
    }

    private fun ensureClosedJoined(joined: JoiningState) {
        val closed = closed ?: return
        this.joining = null

        if (!joined.delegateClose) {
            joined.delegatedTo.flush()
            joined.complete()
            return
        }

        // writing state could be if we are inside of copyDirect loop
        // so in this case we shouldn't close channel
        // otherwise few bytes could be lost
        // it will be closed later in copyDirect's finalization
        // so we only do flush
        val writing = joined.delegatedTo.state.let {
            it is ReadWriteBufferState.Writing || it is ReadWriteBufferState.ReadingWriting
        }
        if (closed.cause != null || !writing) {
            joined.delegatedTo.close(closed.cause)
        } else {
            joined.delegatedTo.flush()
        }

        joined.complete()
    }

    private fun writeAsMuchAsPossible(src: ByteBuffer): Int {
        writing { dst, state ->
            var written = 0
            val srcLimit = src.limit()

            do {
                val srcRemaining = srcLimit - src.position()
                if (srcRemaining == 0) break
                val possibleSize = state.tryWriteAtMost(minOf(srcRemaining, dst.remaining()))
                if (possibleSize == 0) break
                require(possibleSize > 0)

                src.limit(src.position() + possibleSize)
                dst.put(src)

                written += possibleSize

                dst.prepareBuffer(dst.carryIndex(writePosition + written), state.availableForWrite)
            } while (true)

            src.limit(srcLimit)

            dst.bytesWritten(state, written)

            return written
        }

        return 0
    }

    private fun writeAsMuchAsPossible(src: io.ktor.utils.io.core.Buffer): Int {
        writing { dst, state ->
            var written = 0

            do {
                val srcSize = src.readRemaining
                val possibleSize = state.tryWriteAtMost(minOf(srcSize, dst.remaining()))
                if (possibleSize == 0) break

                src.readFully(dst, possibleSize)

                written += possibleSize

                dst.prepareBuffer(dst.carryIndex(writePosition + written), state.availableForWrite)
            } while (true)

            dst.bytesWritten(state, written)

            return written
        }

        return 0
    }

    private fun writeAsMuchAsPossible(src: ByteArray, offset: Int, length: Int): Int {
        writing { dst, state ->
            var written = 0

            do {
                val possibleSize = state.tryWriteAtMost(minOf(length - written, dst.remaining()))
                if (possibleSize == 0) break
                require(possibleSize > 0)

                dst.put(src, offset + written, possibleSize)
                written += possibleSize

                dst.prepareBuffer(dst.carryIndex(writePosition + written), state.availableForWrite)
            } while (true)

            dst.bytesWritten(state, written)

            return written
        }

        return 0
    }

    override suspend fun writeFully(src: ByteArray, offset: Int, length: Int) {
        joining?.let { resolveDelegation(this, it)?.let { return it.writeFully(src, offset, length) } }

        var rem = length
        var off = offset

        while (rem > 0) {
            val s = writeAsMuchAsPossible(src, off, rem)
            if (s == 0) break

            off += s
            rem -= s
        }

        if (rem == 0) return

        return writeFullySuspend(src, off, rem)
    }

    private suspend fun writeFullySuspend(src: ByteArray, offset: Int, length: Int) {
        var currentOffset = offset
        var currentLength = length

        while (currentLength > 0) {
            val copied = writeAvailable(src, currentOffset, currentLength)

            currentOffset += copied
            currentLength -= copied
        }
    }

    override suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int {
        joining?.let { resolveDelegation(this, it)?.let { return it.writeAvailable(src, offset, length) } }

        val size = writeAsMuchAsPossible(src, offset, length)
        if (size > 0) return size
        return writeSuspend(src, offset, length)
    }

    private suspend fun writeSuspend(src: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            tryWriteSuspend(1)

            joining?.let { resolveDelegation(this, it)?.let { return it.writeSuspend(src, offset, length) } }

            val size = writeAsMuchAsPossible(src, offset, length)
            if (size > 0) return size
        }
    }

    override fun writeAvailable(min: Int, block: (ByteBuffer) -> Unit): Int {
        require(min > 0) { "min should be positive" }
        require(min <= BYTE_BUFFER_CAPACITY) { "Min($min) shouldn't be greater than $BYTE_BUFFER_CAPACITY" }

        var result = 0
        var written = false

        writing { dst, state ->
            val locked = state.tryWriteAtLeast(min)
            if (locked <= 0) {
                return@writing
            }

            // here we have locked all remaining for write bytes
            // however we don't know how many bytes will be actually written
            // so later we have to return (locked - actuallyWritten) bytes back

            // it is important to lock bytes to fail concurrent tryLockForRelease
            // once we have locked some bytes, tryLockForRelease will fail so it is safe to use buffer

            dst.prepareBuffer(writePosition, locked)

            val position = dst.position()
            val l = dst.limit()
            block(dst)
            check(l == dst.limit()) { "Buffer limit modified" }

            result = dst.position() - position
            check(result >= 0) { "Position has been moved backward: pushback is not supported" }
            if (result < 0) throw IllegalStateException()

            dst.bytesWritten(state, result)

            if (result < locked) {
                state.completeRead(locked - result) // return back extra bytes (see note above)
                // we use completeRead in spite of that it is write block
                // we don't need to resume write as we are already in writing block
            }

            written = true
        }

        if (!written) {
            return -1
        }

        return result
    }

    override suspend fun write(min: Int, block: (ByteBuffer) -> Unit) {
        require(min > 0) { "min should be positive" }
        require(min <= BYTE_BUFFER_CAPACITY) { "Min($min) should'nt be greater than ($BYTE_BUFFER_CAPACITY)" }

        while (true) {
            val writeAvailable = writeAvailable(min, block)
            if (writeAvailable >= 0) {
                break
            }

            awaitFreeSpaceOrDelegate(min, block)
        }
    }

    private suspend fun awaitFreeSpaceOrDelegate(min: Int, block: (ByteBuffer) -> Unit) {
        writeSuspend(min)
        joining?.let { resolveDelegation(this, it)?.let { return it.write(min, block) } }
    }

    override suspend fun writeWhile(block: (ByteBuffer) -> Boolean) {
        if (!writeWhileNoSuspend(block)) return
        closed?.let { rethrowClosed(it.sendException) }
        return writeWhileSuspend(block)
    }

    private fun writeWhileNoSuspend(block: (ByteBuffer) -> Boolean): Boolean {
        var continueWriting = true

        writing { dst, capacity ->
            continueWriting = writeWhileLoop(dst, capacity, block)
        }

        return continueWriting
    }

    private suspend fun writeWhileSuspend(block: (ByteBuffer) -> Boolean) {
        var continueWriting = true

        writing { dst, capacity ->
            while (true) {
                writeSuspend(1)
                if (joining != null) break
                if (!writeWhileLoop(dst, capacity, block)) {
                    continueWriting = false
                    break
                }
                if (closed != null) break
            }
        }

        if (!continueWriting) return
        closed?.let { rethrowClosed(it.sendException) }
        joining?.let { return writeWhile(block) }
    }

    // it should be writing state set to use this function
    private fun writeWhileLoop(dst: ByteBuffer, capacity: RingBufferCapacity, block: (ByteBuffer) -> Boolean): Boolean {
        var continueWriting = true
        val bufferLimit = dst.capacity() - reservedSize

        while (continueWriting) {
            val locked = capacity.tryWriteAtLeast(1) // see comments in [write]
            if (locked == 0) break

            val position = writePosition
            val l = (position + locked).coerceAtMost(bufferLimit)
            dst.limit(l)
            dst.position(position)

            continueWriting = try {
                block(dst)
            } catch (cause: Throwable) {
                capacity.completeRead(locked)
                throw cause
            }

            check(dst.limit() == l) { "Buffer limit modified." }
            val actuallyWritten = dst.position() - position
            check(actuallyWritten >= 0) { "Position has been moved backward: pushback is not supported." }

            dst.bytesWritten(capacity, actuallyWritten)
            if (actuallyWritten < locked) {
                capacity.completeRead(locked - actuallyWritten) // return back extra bytes
                // it is important to use completeRead in spite of that we are writing here
                // no need to resume read here
            }
        }

        return continueWriting
    }

    @Suppress("DEPRECATION")
    private val readSession = ReadSessionImpl(this)

    @Suppress("DEPRECATION")
    override fun startReadSession(): SuspendableReadSession {
        return readSession
    }

    override fun endReadSession() {
        readSession.completed()
        val state = state
        if (state is ReadWriteBufferState.Reading || state is ReadWriteBufferState.ReadingWriting) {
            restoreStateAfterRead()
            tryTerminate()
        }
    }

    @Suppress("DEPRECATION")
    override fun beginWriteSession(): WriterSuspendSession? {
        return writeSession.also { it.begin() }
    }

    override fun endWriteSession(written: Int) {
        writeSession.written(written)
        writeSession.complete()
    }

    @Deprecated("Use read { } instead.")
    override fun readSession(consumer: ReadSession.() -> Unit) {
        lookAhead {
            try {
                consumer(readSession)
            } finally {
                readSession.completed()
            }
        }
    }

    @Deprecated("Use read { } instead.")
    override suspend fun readSuspendableSession(consumer: suspend SuspendableReadSession.() -> Unit) {
        lookAheadSuspend {
            try {
                consumer(readSession)
            } finally {
                readSession.completed()
            }
        }
    }

    override suspend fun read(min: Int, consumer: (ByteBuffer) -> Unit) {
        require(min >= 0) { "min should be positive or zero" }

        val read = reading {
            val av = it.availableForRead
            if (av <= 0 || av < min) {
                return@reading false
            }

            val position = this.position()
            val oldLimit = this.limit()
            consumer(this)

            check(oldLimit == this.limit()) { "Buffer limit modified." }
            val delta = position() - position

            check(delta >= 0) { "Position has been moved backward: pushback is not supported." }
            check(it.tryReadExact(delta))

            bytesRead(it, delta)

            return@reading true
        }

        if (!read) {
            if (isClosedForRead && min > 0) {
                throw EOFException("Got EOF but at least $min bytes were expected")
            }

            readBlockSuspend(min, consumer)
        }
    }

    override suspend fun discard(max: Long): Long {
        require(max >= 0) { "max shouldn't be negative: $max" }

        var discarded = 0L

        reading {
            val n = it.tryReadAtMost(minOf(Int.MAX_VALUE.toLong(), max).toInt())
            bytesRead(it, n)
            discarded += n
            true
        }

        if (discarded == max || isClosedForRead) {
            return discarded
        }

        return discardSuspend(discarded, max)
    }

    private suspend fun discardSuspend(discarded0: Long, max: Long): Long {
        var discarded = discarded0

        while (discarded < max) {
            val rc = reading {
                val n = it.tryReadAtMost(minOf(Int.MAX_VALUE.toLong(), max - discarded).toInt())
                bytesRead(it, n)
                discarded += n

                true
            }

            if (!rc) {
                if (isClosedForRead || !readSuspend(1)) break
            }
        }

        return discarded
    }

    private suspend fun readBlockSuspend(min: Int, block: (ByteBuffer) -> Unit) {
        if (!readSuspend(min.coerceAtLeast(1))) {
            if (min > 0) {
                throw EOFException("Got EOF but at least $min bytes were expected")
            }

            return
        }

        read(min, block)
    }

    override suspend fun writePacket(packet: ByteReadPacket) {
        joining?.let { resolveDelegation(this, it)?.let { return it.writePacket(packet) } }

        try {
            while (packet.isNotEmpty) {
                if (tryWritePacketPart(packet) == 0) break
            }
        } catch (cause: Throwable) {
            packet.release()
            throw cause
        }

        if (packet.remaining > 0) {
            joining?.let { resolveDelegation(this, it)?.let { return it.writePacket(packet) } }
            return writePacketSuspend(packet)
        }
    }

    private suspend fun writePacketSuspend(packet: ByteReadPacket) {
        try {
            while (packet.isNotEmpty) {
                writeSuspend(1)

                joining?.let { resolveDelegation(this, it)?.let { return it.writePacket(packet) } }
                tryWritePacketPart(packet)
            }
        } finally {
            packet.release()
        }
    }

    private fun tryWritePacketPart(packet: ByteReadPacket): Int {
        var copied = 0
        writing { dst, state ->
            val size = state.tryWriteAtMost(minOf(packet.remaining, dst.remaining().toLong()).toInt())
            if (size > 0) {
                dst.limit(dst.position() + size)
                packet.readFully(dst)
                dst.bytesWritten(state, size)
            }
            copied = size
        }

        return copied
    }

    @Deprecated("Use read { } instead.")
    override fun <R> lookAhead(visitor: LookAheadSession.() -> R): R {
        closedCause?.let { return visitor(FailedLookAhead(it)) }
        if (state === ReadWriteBufferState.Terminated) {
            return visitor(TerminatedLookAhead)
        }

        var result: R? = null
        val continueReading = reading {
            result = visitor(this@ByteBufferChannel)
            true
        }

        if (!continueReading) {
            closedCause?.let { return visitor(FailedLookAhead(it)) }
            return visitor(TerminatedLookAhead)
        }

        return result!!
    }

    @Deprecated("Use read { } instead.")
    override suspend fun <R> lookAheadSuspend(visitor: suspend LookAheadSuspendSession.() -> R): R {
        closedCause?.let { return visitor(FailedLookAhead(it)) }
        if (state === ReadWriteBufferState.Terminated) {
            return visitor(TerminatedLookAhead)
        }

        var result: Any? = null
        val rc = reading {
            result = visitor(this@ByteBufferChannel)
            true
        }

        if (!rc) {
            closedCause?.let { return visitor(FailedLookAhead(it)) }
            if (state === ReadWriteBufferState.Terminated) {
                return visitor(TerminatedLookAhead)
            }

            try {
                result = visitor(this)
            } finally {
                val stateSnapshot = state

                if (!stateSnapshot.idle && stateSnapshot !== ReadWriteBufferState.Terminated) {
                    if (stateSnapshot is ReadWriteBufferState.Reading ||
                        stateSnapshot is ReadWriteBufferState.ReadingWriting
                    ) {
                        restoreStateAfterRead()
                    }
                    tryTerminate()
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return result as R
    }

    private val writeSession = WriteSessionImpl(this)

    @Deprecated("Use write { } instead.")
    override suspend fun writeSuspendSession(visitor: suspend WriterSuspendSession.() -> Unit) {
        val session = writeSession

        session.begin()
        try {
            visitor(session)
        } finally {
            session.complete()
        }
    }

    override fun consumed(n: Int) {
        require(n >= 0)

        state.let { s ->
            if (!s.capacity.tryReadExact(n)) {
                throw IllegalStateException("Unable to consume $n bytes: not enough available bytes")
            }
            if (n > 0) {
                s.readBuffer.bytesRead(s.capacity, n)
            }
        }
    }

    final override suspend fun awaitAtLeast(n: Int): Boolean {
        require(n >= 0) { "atLeast parameter shouldn't be negative: $n" }
        require(n <= 4088) { "atLeast parameter shouldn't be larger than max buffer size of 4088: $n" }

        if (state.capacity.availableForRead >= n) {
            if (state.idle || state is ReadWriteBufferState.Writing) setupStateForRead()
            return true
        }

        return when {
            state.idle || state is ReadWriteBufferState.Writing -> awaitAtLeastSuspend(n)
            n == 1 -> readSuspendImpl(1)
            else -> readSuspend(n)
        }
    }

    private suspend fun awaitAtLeastSuspend(n: Int): Boolean {
        val rc = readSuspend(n)
        if (rc && state.idle) {
            setupStateForRead()
        }
        return rc
    }

    override fun request(skip: Int, atLeast: Int): ByteBuffer? {
        return state.let { s ->
            val available = s.capacity.availableForRead
            val rp = readPosition

            if (available < atLeast + skip) return null
            if (s.idle || (s !is ReadWriteBufferState.Reading && s !is ReadWriteBufferState.ReadingWriting)) {
                setupStateForRead() ?: return null
                return request(skip, atLeast)
            }

            val buffer = s.readBuffer

            val position = buffer.carryIndex(rp + skip)
            buffer.prepareBuffer(position, available - skip)

            if (buffer.remaining() >= atLeast) buffer else null
        }
    }

    private inline fun consumeEachBufferRangeFast(
        last: Boolean,
        visitor: (buffer: ByteBuffer, last: Boolean) -> Boolean
    ): Boolean {
        val rc = reading {
            do {
                if (!hasRemaining() && !last) break

                val rc = visitor(this, last)
                afterBufferVisited(this, it)
                if (!rc || (last && !hasRemaining())) {
                    return true
                }
            } while (true)

            last
        }

        if (!rc && closed != null) {
            visitor(EmptyByteBuffer, true)
            return true
        }

        return rc
    }

    private fun afterBufferVisited(buffer: ByteBuffer, capacity: RingBufferCapacity): Int {
        val consumed = buffer.position() - readPosition
        if (consumed > 0) {
            if (!capacity.tryReadExact(consumed)) throw IllegalStateException("Consumed more bytes than available")

            buffer.bytesRead(capacity, consumed)
            buffer.prepareBuffer(readPosition, capacity.availableForRead)
        }

        return consumed
    }

    private suspend fun readUTF8LineToAscii(out: Appendable, limit: Int): Boolean {
        if (state === ReadWriteBufferState.Terminated) {
            val cause = closedCause
            if (cause != null) throw cause
            return false
        }

        return readUTF8LineToUtf8Suspend(out, limit)
    }

    private suspend fun readUTF8LineToUtf8Suspend(
        out: Appendable,
        limit: Int
    ): Boolean {
        var consumed = 0
        var required = 1
        var caret = false
        var newLine = false

        val output = CharArray(8 * 1024)
        var transferBuffer: ByteBuffer? = null
        var transferredRemaining = 0
        while (!isClosedForRead && !newLine && !caret && (limit == Int.MAX_VALUE || consumed <= limit)) {
            try {
                read(required) { buffer ->
                    val oldPosition = buffer.position()
                    val bufferToDecode = transferBuffer?.also {
                        val oldLimit = buffer.limit()
                        buffer.limit(min(buffer.limit(), buffer.position() + it.remaining()))
                        it.put(buffer)
                        it.flip()
                        buffer.limit(oldLimit)
                    } ?: buffer

                    val readLimit = if (limit == Int.MAX_VALUE) output.size else minOf(output.size, limit - consumed)
                    val decodeResult = bufferToDecode.decodeUTF8Line(output, 0, readLimit)

                    transferBuffer?.let {
                        buffer.position(oldPosition + it.position() - transferredRemaining)
                        BufferPool.recycle(it)
                        transferBuffer = null
                        transferredRemaining = 0
                    }

                    val decoded = (decodeResult shr 32).toInt()
                    val requiredBytes = (decodeResult and 0xffffffffL).toInt()

                    required = max(1, requiredBytes)

                    if (requiredBytes == -1) {
                        newLine = true
                    }

                    if (requiredBytes != -1 &&
                        buffer.hasRemaining() &&
                        buffer[buffer.position()] == '\r'.code.toByte()
                    ) {
                        buffer.position(buffer.position() + 1)
                        caret = true
                    }

                    if (requiredBytes != -1 &&
                        buffer.hasRemaining() &&
                        buffer[buffer.position()] == '\n'.code.toByte()
                    ) {
                        buffer.position(buffer.position() + 1)
                        newLine = true
                    }

                    if (out is StringBuilder) {
                        out.append(output, 0, decoded)
                    } else {
                        val charBuffer = CharBuffer.wrap(output, 0, decoded)
                        out.append(charBuffer, 0, decoded)
                    }

                    consumed += decoded

                    if (decoded == 0 && buffer.remaining() < requiredBytes) {
                        transferBuffer = BufferPool.borrow().also {
                            transferredRemaining = buffer.remaining()
                            it.put(buffer)
                        }
                    }

                    if (limit != Int.MAX_VALUE && consumed >= limit && !newLine) {
                        throw TooLongLineException("Line is longer than limit")
                    }
                }
            } catch (_: EOFException) {
                // Ignored by the contract of [ByteReadChannel.readUTF8LineTo] method
            }
        }

        if (transferBuffer != null) {
            BufferPool.recycle(transferBuffer!!)
        }

        if (!isClosedForRead && caret && !newLine) {
            try {
                read(1) {
                    if (it[it.position()] == '\n'.code.toByte()) {
                        it.position(it.position() + 1)
                        newLine = true
                    }
                }
            } catch (_: EOFException) {
                // Ignored by the contract of [ByteReadChannel.readUTF8LineTo] method
            }
        }

        return (isClosedForRead && consumed > 0) || (newLine || caret)
    }

    override suspend fun <A : Appendable> readUTF8LineTo(out: A, limit: Int): Boolean =
        readUTF8LineToAscii(out, limit)

    override suspend fun readUTF8Line(limit: Int): String? {
        val sb = StringBuilder()
        if (!readUTF8LineTo(sb, limit)) {
            return null
        }

        return sb.toString()
    }

    override suspend fun readRemaining(limit: Long): ByteReadPacket = if (isClosedForWrite) {
        closedCause?.let { rethrowClosed(it) }
        remainingPacket(limit)
    } else {
        readRemainingSuspend(limit)
    }

    private fun remainingPacket(limit: Long): ByteReadPacket = buildPacket {
        var remaining = limit
        writeWhile { buffer ->
            if (buffer.writeRemaining.toLong() > remaining) {
                buffer.resetForWrite(remaining.toInt())
            }

            val rc = readAsMuchAsPossible(buffer)
            remaining -= rc
            remaining > 0L && !isClosedForRead
        }
    }

    private suspend fun readRemainingSuspend(
        limit: Long
    ): ByteReadPacket = buildPacket {
        var remaining = limit
        writeWhile { buffer ->
            if (buffer.writeRemaining.toLong() > remaining) {
                buffer.resetForWrite(remaining.toInt())
            }

            val rc = readAsMuchAsPossible(buffer)
            remaining -= rc
            remaining > 0L && !isClosedForRead && readSuspend(1)
        }

        closedCause?.let { throw it }
    }

    private fun resumeReadOp() {
        _readOp.getAndSet(null)?.apply {
            val closedCause = closed?.cause
            when {
                closedCause != null -> resumeWithException(closedCause)
                else -> resume(true)
            }
        }
    }

    private inline fun resumeReadOp(exception: () -> Throwable) {
        _readOp.getAndSet(null)?.resumeWithException(exception())
    }

    private fun resumeWriteOp() {
        while (true) {
            val writeOp = writeOp ?: return
            val closed = closed
            if (closed == null && joining != null) {
                val state = state
                if (state !is ReadWriteBufferState.Writing &&
                    state !is ReadWriteBufferState.ReadingWriting &&
                    state !== ReadWriteBufferState.Terminated
                ) {
                    return
                }
            }

            if (_writeOp.compareAndSet(writeOp, null)) {
                if (closed == null) writeOp.resume(Unit) else writeOp.resumeWithException(closed.sendException)
                return
            }
        }
    }

    private fun resumeClosed(cause: Throwable?) {
        _readOp.getAndSet(null)?.let { continuation ->
            if (cause != null) {
                continuation.resumeWithException(cause)
            } else {
                continuation.resume(state.capacity.availableForRead > 0)
            }
        }

        _writeOp.getAndSet(null)?.resumeWithException(
            cause ?: ClosedWriteChannelException(DEFAULT_CLOSE_MESSAGE)
        )
    }

    /**
     * Suspend until the channel has bytes to read or gets closed.
     * Throws exception if the channel was closed with an error.
     */
    override suspend fun awaitContent() {
        readSuspend(1)
    }

    private suspend fun readSuspend(size: Int): Boolean {
        val capacity = state.capacity
        if (capacity.availableForRead >= size) return true

        closed?.let { closedValue ->
            closedValue.cause?.let { rethrowClosed(it) }
            val afterCapacity = state.capacity
            val flush = afterCapacity.flush()
            val result = flush && afterCapacity.availableForRead >= size
            if (readOp != null) throw IllegalStateException("Read operation is already in progress")
            return result
        }

        if (size == 1) {
            return readSuspendImpl(1)
        }

        return readSuspendLoop(size)
    }

    private suspend fun readSuspendLoop(size: Int): Boolean {
        do {
            val capacity = state.capacity
            if (capacity.availableForRead >= size) return true

            closed?.let { value ->
                if (value.cause != null) rethrowClosed(value.cause)
                val afterCapacity = state.capacity
                val result = afterCapacity.flush() && afterCapacity.availableForRead >= size
                if (readOp != null) throw IllegalStateException("Read operation is already in progress")
                return result
            }

            if (!readSuspendImpl(size)) return false
        } while (true)
    }

    private val readSuspendContinuationCache = CancellableReusableContinuation<Boolean>()

    @Suppress("NOTHING_TO_INLINE")
    private inline fun readSuspendPredicate(size: Int): Boolean {
        val state = state

        return state.capacity.availableForRead < size &&
            (
                joining == null ||
                    writeOp == null ||
                    (state !== ReadWriteBufferState.IdleEmpty && state !is ReadWriteBufferState.IdleNonEmpty)
                )
    }

    private fun suspensionForSize(size: Int, continuation: Continuation<Boolean>): Any {
        do {
            if (!readSuspendPredicate(size)) {
                continuation.resume(true)
                break
            }

            closed?.let {
                if (it.cause != null) {
                    continuation.resumeWithException(it.cause)
                    return COROUTINE_SUSPENDED
                }

                val flush = state.capacity.flush()
                val hasEnoughBytes = state.capacity.availableForRead >= size
                continuation.resume(flush && hasEnoughBytes)
                return COROUTINE_SUSPENDED
            }
        } while (!setContinuation({ readOp }, _readOp, continuation, { closed == null && readSuspendPredicate(size) }))

        return COROUTINE_SUSPENDED
    }

    private suspend fun readSuspendImpl(size: Int): Boolean {
        if (!readSuspendPredicate(size)) {
            return true
        }

        try {
            return suspendCoroutineUninterceptedOrReturn { ucont ->
                val cache = readSuspendContinuationCache
                suspensionForSize(size, cache)
                cache.completeSuspendBlock(ucont.intercepted())
            }
        } catch (cause: Throwable) {
            readOp = null
            // restore state
            throw cause
        }
    }

    private fun shouldResumeReadOp(): Boolean = joining != null &&
        (state === ReadWriteBufferState.IdleEmpty || state is ReadWriteBufferState.IdleNonEmpty)

    private fun writeSuspendPredicate(size: Int): Boolean {
        val joined = joining
        val state = state
        val closed = closed

        return when {
            closed != null -> false
            joined == null -> state.capacity.availableForWrite < size && state !== ReadWriteBufferState.IdleEmpty
            else ->
                state !== ReadWriteBufferState.Terminated &&
                    state !is ReadWriteBufferState.Writing &&
                    state !is ReadWriteBufferState.ReadingWriting
        }
    }

    private val writeSuspendContinuationCache = CancellableReusableContinuation<Unit>()

    @Volatile
    private var writeSuspensionSize: Int = 0

    private val writeSuspension = { ucont: Continuation<Unit> ->
        val size = writeSuspensionSize

        do {
            closed?.sendException?.let { rethrowClosed(it) }
            if (!writeSuspendPredicate(size)) {
                ucont.resume(Unit)
                break
            }
        } while (!setContinuation({ writeOp }, _writeOp, ucont.intercepted(), { writeSuspendPredicate(size) }))

        flushImpl(minWriteSize = size)

        if (shouldResumeReadOp()) {
            resumeReadOp()
        }

        COROUTINE_SUSPENDED
    }

    internal suspend fun tryWriteSuspend(size: Int) {
        if (!writeSuspendPredicate(size)) {
            closed?.sendException?.let { rethrowClosed(it) }
            return
        }

        writeSuspensionSize = size
        if (attachedJob != null) {
            return suspendCoroutineUninterceptedOrReturn(writeSuspension)
        }

        return suspendCoroutineUninterceptedOrReturn { raw ->
            val c = writeSuspendContinuationCache
            writeSuspension(c)
            c.completeSuspendBlock(raw.intercepted())
        }
    }

    private suspend fun writeSuspend(size: Int) {
        while (writeSuspendPredicate(size)) {
            suspendCancellableCoroutine<Unit> { c ->
                writeSuspendBlock(size, c)
            }
        }

        closed?.sendException?.let { rethrowClosed(it) }
    }

    private fun writeSuspendBlock(size: Int, c: CancellableContinuation<Unit>) {
        do {
            closed?.sendException?.let { rethrowClosed(it) }
            if (!writeSuspendPredicate(size)) {
                c.resume(Unit)
                break
            }
        } while (!setContinuation({ writeOp }, _writeOp, c, { writeSuspendPredicate(size) }))

        flushImpl(minWriteSize = size)

        if (shouldResumeReadOp()) {
            resumeReadOp()
        }
    }

    private inline fun <T, C : Continuation<T>> setContinuation(
        getter: () -> C?,
        updater: AtomicRef<C?>,
        continuation: C,
        predicate: () -> Boolean
    ): Boolean {
        while (true) {
            val current = getter()
            check(current == null) { "Operation is already in progress" }

            if (!predicate()) {
                return false
            }

            if (updater.compareAndSet(null, continuation)) {
                return (predicate() || !updater.compareAndSet(continuation, null))
            }
        }
    }

    private fun newBuffer(): ReadWriteBufferState.Initial = pool.borrow().apply {
        capacity.resetForWrite()
    }

    private fun releaseBuffer(buffer: ReadWriteBufferState.Initial) {
        pool.recycle(buffer)
    }

    override suspend fun peekTo(
        destination: Memory,
        destinationOffset: Long,
        offset: Long,
        min: Long,
        max: Long
    ): Long {
        var bytesCopied = 0
        val desiredSize = (min + offset).coerceAtMost(4088L).toInt()

        try {
            read(desiredSize) { nioBuffer ->
                if (nioBuffer.remaining() > offset) {
                    val view = nioBuffer.duplicate()!!
                    view.position(view.position() + offset.toInt())

                    val oldLimit = view.limit()
                    val canCopyToDestination = minOf(max, destination.size - destinationOffset)
                    val newLimit = minOf(view.limit().toLong(), canCopyToDestination + offset)
                    view.limit(newLimit.toInt())
                    bytesCopied = view.remaining()
                    view.moveTo(destination, destinationOffset.toInt())
                    view.limit(oldLimit)
                }
            }
        } catch (_: EOFException) {
            // ignored by the contract of peekTo method
        }

        return bytesCopied.toLong()
    }

    override fun toString(): String = "ByteBufferChannel(${hashCode()}, $state)"

    companion object {
        private const val ReservedLongIndex: Int = -8
    }
}

private fun rethrowClosed(cause: Throwable): Nothing {
    val clone = try {
        tryCopyException(cause, cause)
    } catch (_: Throwable) {
        null
    }

    throw clone ?: cause
}
