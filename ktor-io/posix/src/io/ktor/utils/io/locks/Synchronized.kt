/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.locks

import io.ktor.io.interop.mutex.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.AtomicNativePtr
import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.*
import kotlin.native.internal.NativePtr

/**
 * [SynchronizedObject] from `kotlinx.atomicfu.locks`
 *
 * [SynchronizedObject] is designed for inheritance. You write `class MyClass : SynchronizedObject()`
 * and then use `synchronized(instance) { ... }` extension function similarly to the synchronized function
 * from the standard library that is available for JVM.
 * The [SynchronizedObject] superclass gets erased (transformed to Any) on JVM and JS,
 * with `synchronized` leaving no trace in the code on JS and getting replaced with built-in monitors for locking on JVM.
 */
@OptIn(ExperimentalForeignApi::class)
@InternalAPI
public actual open class SynchronizedObject {

    protected val lock: AtomicReference<LockState> = AtomicReference(LockState(Status.UNLOCKED, 0, 0))

    /**
     * Acquires the lock. If the lock is already held by another thread, the current thread
     * will block until it can acquire the lock.
     */
    public fun lock() {
        val currentThreadId = pthread_self()!!
        while (true) {
            val state = lock.value
            when (state.status) {
                Status.UNLOCKED -> {
                    val thinLock = LockState(Status.THIN, 1, 0, currentThreadId)
                    if (lock.compareAndSet(state, thinLock)) {
                        return
                    }
                }

                Status.THIN -> {
                    if (currentThreadId == state.ownerThreadId) {
                        // reentrant lock
                        val thinNested = LockState(Status.THIN, state.nestedLocks + 1, state.waiters, currentThreadId)
                        if (lock.compareAndSet(state, thinNested)) {
                            return
                        }
                    } else {
                        // another thread is trying to take this lock -> allocate native mutex
                        val mutex = mutexPool.allocate()
                        mutex.lock()
                        val fatLock = LockState(
                            Status.FAT,
                            state.nestedLocks,
                            state.waiters + 1,
                            state.ownerThreadId,
                            mutex
                        )
                        if (lock.compareAndSet(state, fatLock)) {
                            // block the current thread waiting for the owner thread to release the permit
                            mutex.lock()
                            tryLockAfterResume(currentThreadId)
                            return
                        } else {
                            // return permit taken for the owner thread and release mutex back to the pool
                            mutex.unlock()
                            mutexPool.release(mutex)
                        }
                    }
                }

                Status.FAT -> {
                    if (currentThreadId == state.ownerThreadId) {
                        // reentrant lock
                        val nestedFatLock = LockState(
                            Status.FAT,
                            state.nestedLocks + 1,
                            state.waiters,
                            state.ownerThreadId,
                            state.mutex
                        )
                        if (lock.compareAndSet(state, nestedFatLock)) return
                    } else if (state.ownerThreadId != null) {
                        val fatLock = LockState(
                            Status.FAT,
                            state.nestedLocks,
                            state.waiters + 1,
                            state.ownerThreadId,
                            state.mutex
                        )
                        if (lock.compareAndSet(state, fatLock)) {
                            fatLock.mutex!!.lock()
                            tryLockAfterResume(currentThreadId)
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * Attempts to acquire the lock. If the lock is available, it will be acquired, and this
     * function will return true. If the lock is already held by another thread, this function
     * will return false immediately without blocking.
     *
     * @return true if the lock was acquired, false otherwise.
     */
    public fun tryLock(): Boolean {
        val currentThreadId = pthread_self()
        while (true) {
            val state = lock.value
            if (state.status == Status.UNLOCKED) {
                val thinLock = LockState(Status.THIN, 1, 0, currentThreadId)
                if (lock.compareAndSet(state, thinLock)) {
                    return true
                }
            } else {
                if (currentThreadId == state.ownerThreadId) {
                    val nestedLock = LockState(
                        state.status,
                        state.nestedLocks + 1,
                        state.waiters,
                        currentThreadId,
                        state.mutex
                    )
                    if (lock.compareAndSet(state, nestedLock)) {
                        return true
                    }
                } else {
                    return false
                }
            }
        }
    }

    /**
     * Releases the lock. If the current thread holds the lock, it will be released, allowing
     * other threads to acquire it.
     */
    public fun unlock() {
        val currentThreadId = pthread_self()
        while (true) {
            val state = lock.value
            require(currentThreadId == state.ownerThreadId) {
                "Thin lock may be only released by the owner thread, expected: ${state.ownerThreadId}, real: $currentThreadId" // ktlint-disable max-line-length
            }
            when (state.status) {
                Status.THIN -> {
                    // nested unlock
                    if (state.nestedLocks == 1) {
                        val unlocked = LockState(Status.UNLOCKED, 0, 0)
                        if (lock.compareAndSet(state, unlocked)) {
                            return
                        }
                    } else {
                        val releasedNestedLock =
                            LockState(Status.THIN, state.nestedLocks - 1, state.waiters, state.ownerThreadId)
                        if (lock.compareAndSet(state, releasedNestedLock)) {
                            return
                        }
                    }
                }

                Status.FAT -> {
                    if (state.nestedLocks == 1) {
                        // last nested unlock -> release completely, resume some waiter
                        val releasedLock = LockState(Status.FAT, 0, state.waiters - 1, null, state.mutex)
                        if (lock.compareAndSet(state, releasedLock)) {
                            releasedLock.mutex!!.unlock()
                            return
                        }
                    } else {
                        // lock is still owned by the current thread
                        val releasedLock =
                            LockState(
                                Status.FAT,
                                state.nestedLocks - 1,
                                state.waiters,
                                state.ownerThreadId,
                                state.mutex
                            )
                        if (lock.compareAndSet(state, releasedLock)) {
                            return
                        }
                    }
                }

                else -> error("It is not possible to unlock the mutex that is not obtained")
            }
        }
    }

    private fun tryLockAfterResume(threadId: pthread_t) {
        while (true) {
            val state = lock.value
            val newState = if (state.waiters == 0) {
                // deflate
                LockState(Status.THIN, 1, 0, threadId)
            } else {
                LockState(Status.FAT, 1, state.waiters, threadId, state.mutex)
            }
            if (lock.compareAndSet(state, newState)) {
                if (state.waiters == 0) {
                    state.mutex!!.unlock()
                    mutexPool.release(state.mutex)
                }
                return
            }
        }
    }

    @OptIn(FreezingIsDeprecated::class)
    protected class LockState(
        public val status: Status,
        public val nestedLocks: Int,
        public val waiters: Int,
        public val ownerThreadId: pthread_t? = null,
        public val mutex: CPointer<ktor_mutex_node_t>? = null
    ) {
        init {
            freeze()
        }
    }

    protected enum class Status { UNLOCKED, THIN, FAT }

    private fun CPointer<ktor_mutex_node_t>.lock() = ktor_lock(this.pointed.mutex)

    private fun CPointer<ktor_mutex_node_t>.unlock() = ktor_unlock(this.pointed.mutex)
}

/**
 * [ReentrantLock] from kotlinx.atomicfu.locks
 *
 * [ReentrantLock] is designed for delegation. You write `val lock = reentrantLock()` to construct its instance and
 * use `lock/tryLock/unlock` functions or `lock.withLock { ... }` extension function similarly to
 * the way jucl.ReentrantLock is used on JVM. On JVM it is a typealias to the later class, erased on JS.
 */
@InternalAPI
public actual typealias ReentrantLock = SynchronizedObject

/**
 * Creates a new [ReentrantLock] instance.
 */
@InternalAPI
public actual fun reentrantLock(): ReentrantLock = ReentrantLock()

/**
 * Executes the given [block] of code while holding the specified [ReentrantLock]. This function
 * simplifies the process of acquiring and releasing a lock safely.
 *
 * Usage:
 * ```
 * val lock = reentrantLock()
 * lock.withLock {
 *     // Critical section of code
 * }
 * ```
 */
@InternalAPI
public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

/**
 * Executes the given [block] of code within a synchronized block, using the specified
 * [SynchronizedObject] as the synchronization object. This function simplifies the process
 * of creating synchronized blocks safely.
 *
 * Usage:
 * ```
 * val syncObject = SynchronizedObject()
 * synchronized(syncObject) {
 *     // Critical section of code
 * }
 * ```
 */
@InternalAPI
public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}

private const val INITIAL_POOL_CAPACITY = 64

private val mutexPool by lazy { MutexPool(INITIAL_POOL_CAPACITY) }

@OptIn(ExperimentalForeignApi::class)
private class MutexPool(capacity: Int) {
    private val top = AtomicNativePtr(NativePtr.NULL)

    private val mutexes = nativeHeap.allocArray<ktor_mutex_node_t>(capacity) { ktor_mutex_node_init(ptr) }

    init {
        for (i in 0 until capacity) {
            release(interpretCPointer(mutexes.rawValue.plus(i * sizeOf<ktor_mutex_node_t>()))!!)
        }
    }

    private fun allocMutexNode() = nativeHeap.alloc<ktor_mutex_node_t> { ktor_mutex_node_init(ptr) }.ptr

    fun allocate(): CPointer<ktor_mutex_node_t> = pop() ?: allocMutexNode()

    fun release(mutexNode: CPointer<ktor_mutex_node_t>) {
        while (true) {
            val oldTop = interpretCPointer<ktor_mutex_node_t>(top.value)
            mutexNode.pointed.next = oldTop
            if (top.compareAndSet(oldTop.rawValue, mutexNode.rawValue)) {
                return
            }
        }
    }

    private fun pop(): CPointer<ktor_mutex_node_t>? {
        while (true) {
            val oldTop = interpretCPointer<ktor_mutex_node_t>(top.value)
            if (oldTop.rawValue === NativePtr.NULL) {
                return null
            }
            val newHead = oldTop!!.pointed.next
            if (top.compareAndSet(oldTop.rawValue, newHead.rawValue)) {
                return oldTop
            }
        }
    }
}
