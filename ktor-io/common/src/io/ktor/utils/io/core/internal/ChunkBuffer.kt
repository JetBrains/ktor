package io.ktor.utils.io.core.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*

@Suppress("DEPRECATION")
@Deprecated(IO_DEPRECATION_MESSAGE)
public open class ChunkBuffer(
    memory: Memory,
    origin: ChunkBuffer?,
    internal val parentPool: ObjectPool<ChunkBuffer>?
) : Buffer(memory) {
    init {
        require(origin !== this) { "A chunk couldn't be a view of itself." }
    }

    private val nextRef: AtomicRef<ChunkBuffer?> = atomic(null)
    private val refCount = atomic(1)

    /**
     * Reference to an origin buffer view this was copied from
     */
    public var origin: ChunkBuffer? = origin
        private set

    /**
     * Reference to next buffer view. Useful to chain multiple views.
     * @see appendNext
     * @see cleanNext
     */
    public var next: ChunkBuffer?
        get() = nextRef.value
        set(newValue) {
            if (newValue == null) {
                cleanNext()
            } else {
                appendNext(newValue)
            }
        }

    public val referenceCount: Int get() = refCount.value

    private fun appendNext(chunk: ChunkBuffer) {
        if (!nextRef.compareAndSet(null, chunk)) {
            throw IllegalStateException("This chunk has already a next chunk.")
        }
    }

    public fun cleanNext(): ChunkBuffer? {
        return nextRef.getAndSet(null)
    }

    override fun duplicate(): ChunkBuffer = (origin ?: this).let { newOrigin ->
        newOrigin.acquire()
        ChunkBuffer(memory, newOrigin, parentPool).also { copy ->
            duplicateTo(copy)
        }
    }

    public open fun release(pool: ObjectPool<ChunkBuffer>) {
        if (release()) {
            val origin = origin
            if (origin != null) {
                unlink()
                origin.release(pool)
            } else {
                val poolToUse = parentPool ?: pool
                poolToUse.recycle(this)
            }
        }
    }

    internal fun unlink() {
        if (!refCount.compareAndSet(0, -1)) {
            throw IllegalStateException("Unable to unlink: buffer is in use.")
        }

        cleanNext()
        origin = null
    }

    /**
     * Increase ref-count. May fail if already released.
     */
    internal fun acquire() {
        refCount.update { old ->
            if (old <= 0) throw IllegalStateException("Unable to acquire chunk: it is already released.")
            old + 1
        }
    }

    /**
     * Invoked by a pool before return the instance to a user.
     */
    internal fun unpark() {
        refCount.update { old ->
            if (old < 0) {
                throw IllegalStateException("This instance is already disposed and couldn't be borrowed.")
            }
            if (old > 0) {
                throw IllegalStateException("This instance is already in use but somehow appeared in the pool.")
            }

            1
        }
    }

    /**
     * Release ref-count.
     * @return `true` if the last usage was released
     */
    internal fun release(): Boolean {
        return refCount.updateAndGet { old ->
            if (old <= 0) throw IllegalStateException("Unable to release: it is already released.")
            old - 1
        } == 0
    }

    final override fun reset() {
        require(origin == null) { "Unable to reset buffer with origin" }

        super.reset()
        nextRef.value = null
    }

    public companion object {
        public val Pool: ObjectPool<ChunkBuffer> get() = DefaultChunkedBufferPool

        /**
         * A pool that always returns [ChunkBuffer.Empty]
         */
        public val EmptyPool: ObjectPool<ChunkBuffer> = object : ObjectPool<ChunkBuffer> {
            override val capacity: Int get() = 1

            override fun borrow() = Empty

            override fun recycle(instance: ChunkBuffer) {
                require(instance === Empty) { "Only ChunkBuffer.Empty instance could be recycled." }
            }

            override fun dispose() {
            }
        }

        public val Empty: ChunkBuffer = ChunkBuffer(MEMORY_EMPTY, null, EmptyPool)

        internal val NoPool: ObjectPool<ChunkBuffer> = object : NoPoolImpl<ChunkBuffer>() {
            override fun borrow(): ChunkBuffer {
                return ChunkBuffer(DefaultAllocator.alloc(DEFAULT_BUFFER_SIZE), null, this)
            }

            override fun recycle(instance: ChunkBuffer) {
                DefaultAllocator.free(instance.memory)
            }
        }

        internal val NoPoolManuallyManaged: ObjectPool<ChunkBuffer> = object : NoPoolImpl<ChunkBuffer>() {
            override fun borrow(): ChunkBuffer {
                throw UnsupportedOperationException("This pool doesn't support borrow")
            }

            override fun recycle(instance: ChunkBuffer) {
                // do nothing: manually managed objects should be disposed manually
            }
        }
    }
}

/**
 * @return `true` if and only if there are no buffer views that share the same actual buffer. This actually does
 * refcount and only work guaranteed if other views created/not created via [Buffer.duplicate] function.
 * One can instantiate multiple buffers with the same buffer and this function will return `true` in spite of
 * the fact that the buffer is actually shared.
 */
@Suppress("DEPRECATION")
internal fun ChunkBuffer.isExclusivelyOwned(): Boolean = referenceCount == 1
