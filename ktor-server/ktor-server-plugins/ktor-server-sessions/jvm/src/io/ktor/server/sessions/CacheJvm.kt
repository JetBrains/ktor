/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import kotlinx.coroutines.*
import java.lang.ref.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

internal interface CacheReference<out K> {
    val key: K
}

internal open class ReferenceCache<K : Any, V : Any, out R>(
    val calc: suspend (K) -> V,
    val wrapFunction: (K, V, ReferenceQueue<V>) -> R
) : Cache<K, V> where R : Reference<V>, R : CacheReference<K> {
    private val queue = ReferenceQueue<V>()
    private val container = BaseCache { key: K -> forkThreadIfNeeded(); wrapFunction(key, calc(key), queue) }
    private val workerThread by lazy { Thread(ReferenceWorker(container, queue)).apply { isDaemon = true; start() } }

    override suspend fun getOrCompute(key: K): V {
        val ref = container.getOrCompute(key)
        val value = ref.get()

        if (value == null) {
            if (container.invalidate(key, ref)) {
                ref.enqueue()
            }
            return getOrCompute(key)
        }

        return value
    }

    override fun peek(key: K): V? = container.peek(key)?.get()

    override fun invalidate(key: K): V? = container.invalidate(key)?.get()
    override fun invalidate(key: K, value: V): Boolean {
        val ref = container.peek(key)

        if (ref?.get() == value) {
            ref.enqueue()
            return container.invalidate(key, ref)
        }

        return false
    }

    override fun invalidateAll() {
        container.invalidateAll()
    }

    private fun forkThreadIfNeeded() {
        if (!workerThread.isAlive) {
            throw IllegalStateException("Daemon thread is already dead")
        }
    }
}

private class ReferenceWorker<out K : Any, R : CacheReference<K>>(
    owner: Cache<K, R>,
    val queue: ReferenceQueue<*>
) : Runnable {
    private val owner = WeakReference(owner)

    override fun run() {
        do {
            val ref = queue.remove(60000)
            if (ref is CacheReference<*>) {
                @Suppress("UNCHECKED_CAST")
                val cast = ref as R
                val currentOwner = owner.get() ?: break

                currentOwner.invalidate(cast.key, cast)
            }
        } while (!Thread.interrupted() && owner.get() != null)
    }
}

internal class CacheSoftReference<out K, V>(override val key: K, value: V, queue: ReferenceQueue<V>) :
    SoftReference<V>(value, queue), CacheReference<K>

internal class SoftReferenceCache<K : Any, V : Any>(calc: suspend (K) -> V) :
    ReferenceCache<K, V, CacheSoftReference<K, V>>(calc, { k, v, q -> CacheSoftReference(k, v, q) })

internal class BaseTimeoutCache<in K : Any, V : Any>(
    private val timeoutValue: Long,
    private val touchOnGet: Boolean,
    private val delegate: Cache<K, V>
) : Cache<K, V> {
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    private val items = PullableLinkedList<KeyState<K>>()
    private val map = WeakHashMap<K, KeyState<K>>()

    private val workerThread by lazy {
        Thread(TimeoutWorker(this, lock, cond, items)).apply { isDaemon = true; start() }
    }

    override suspend fun getOrCompute(key: K): V {
        if (touchOnGet) {
            pull(key)
        }
        return delegate.getOrCompute(key)
    }

    override fun peek(key: K): V? {
        if (touchOnGet) {
            pull(key, create = false)
        }
        return delegate.peek(key)
    }

    override fun invalidate(key: K): V? {
        remove(key)
        return delegate.invalidate(key)
    }

    override fun invalidate(key: K, value: V): Boolean {
        if (delegate.invalidate(key, value)) {
            remove(key)
            return true
        }
        return false
    }

    override fun invalidateAll() {
        delegate.invalidateAll()
        lock.withLock {
            items.clear()
            cond.signalAll()
        }
    }

    private fun forkIfNeeded() {
        if (!items.isEmpty() && !workerThread.isAlive) {
            throw IllegalStateException("Daemon thread is already dead")
        }
    }

    private fun pull(key: K, create: Boolean = true) {
        lock.withLock {
            val state = if (create) map.getOrPut(key) { KeyState(key, timeoutValue) } else map[key]
            if (state != null) {
                state.touch()
                items.pull(state)
                cond.signalAll()
            }
        }
        forkIfNeeded()
    }

    private fun remove(key: K) {
        lock.withLock {
            map.remove(key)?.let {
                items.remove(it)
                cond.signalAll()
            }
        }
    }
}

private class KeyState<K>(key: K, val timeout: Long) : ListElement<KeyState<K>>() {
    val key: WeakReference<K> = WeakReference(key)
    var lastAccess = System.currentTimeMillis()

    fun touch() {
        lastAccess = System.currentTimeMillis()
    }

    fun timeToWait() = 0L.coerceAtLeast(lastAccess + timeout - System.currentTimeMillis())
}

private class TimeoutWorker<K : Any>(
    owner: BaseTimeoutCache<K, *>,
    val lock: ReentrantLock,
    val cond: Condition,
    val items: PullableLinkedList<KeyState<K>>
) : Runnable {
    private val owner = WeakReference(owner)

    override fun run() {
        do {
            lock.withLock {
                val item = head()
                if (item != null) {
                    val time = item.timeToWait()

                    if (time == 0L) {
                        items.remove(item)
                        val k = item.key.get()
                        if (k != null) {
                            owner.get()?.invalidate(k)
                        }
                    } else {
                        cond.await(time, TimeUnit.MILLISECONDS)
                    }
                }
            }
        } while (!Thread.interrupted() && owner.get() != null)
    }

    private fun head() =
        lock.withLock {
            while (items.isEmpty() && owner.get() != null) {
                cond.await(60, TimeUnit.SECONDS)
            }

            if (owner.get() == null) null else items.head()
        }
}

private abstract class ListElement<E : ListElement<E>> {
    var next: E? = null
    var prev: E? = null
}

private class PullableLinkedList<E : ListElement<E>> {
    private var head: E? = null
    private var tail: E? = null

    fun isEmpty() = head == null
    fun take(): E = head().apply { remove(this) }
    fun head(): E = head ?: throw NoSuchElementException()

    fun add(element: E) {
        require(element.next == null)
        require(element.prev == null)

        val oldHead = head
        if (oldHead != null) {
            element.next = oldHead
            oldHead.prev = element
        }
        head = element
        if (tail == null) {
            tail = element
        }
    }

    fun remove(element: E) {
        if (element == head) {
            head = null
        }
        if (element == tail) {
            tail = null
        }

        element.prev?.next = element.next
        element.next = null
        element.prev = null
    }

    fun clear() {
        head = null
        tail = null
    }

    fun pull(element: E) {
        if (element !== head) {
            remove(element)
            add(element)
        }
    }
}
