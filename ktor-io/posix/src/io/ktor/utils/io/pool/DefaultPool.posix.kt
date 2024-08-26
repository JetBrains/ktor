/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.pool

import io.ktor.utils.io.*
import io.ktor.utils.io.locks.*
import kotlinx.atomicfu.*

@OptIn(InternalAPI::class)
public actual abstract class DefaultPool<T : Any> actual constructor(
    actual final override val capacity: Int
) : ObjectPool<T> {
    private val lock: SynchronizedObject = SynchronizedObject()

    private val instances = mutableListOf<T>()

    private val _allocated = atomic(0)
    private val _released = atomic(0)
    private val _recycled = atomic(0)

    public val inCache: Int get() = instances.size
    public val inUsed: Int get() = _allocated.value - _released.value
    public val allocated: Int get() = _allocated.value
    public val released: Int get() = _released.value
    public val recycled: Int get() = _recycled.value

    protected actual abstract fun produceInstance(): T
    protected actual open fun disposeInstance(instance: T) {}

    protected actual open fun clearInstance(instance: T): T = instance
    protected actual open fun validateInstance(instance: T) {}

    public actual final override fun borrow(): T = synchronized(lock) {
        if (instances.isEmpty()) {
            _allocated.incrementAndGet()
            return@synchronized produceInstance()
        }

        val result = instances.removeAt(instances.lastIndex)
        clearInstance(result)
        return@synchronized result
    }

    public actual final override fun recycle(instance: T) {
        synchronized(lock) {
            _recycled.incrementAndGet()
            validateInstance(instance)
            if (instances.size < capacity) {
                instances.add(instance)
            } else {
                _released.incrementAndGet()
                disposeInstance(instance)
            }
        }
    }

    public actual final override fun dispose() {
        synchronized(lock) {
            instances.forEach { disposeInstance(it) }
            instances.clear()
        }
    }
}
