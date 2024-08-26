/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.locks

import io.ktor.utils.io.*

/**
 * [SynchronizedObject] from `kotlinx.atomicfu.locks`
 *
 * [SynchronizedObject] is designed for inheritance. You write `class MyClass : SynchronizedObject()`
 * and then use `synchronized(instance) { ... }` extension function similarly to the synchronized function
 * from the standard library that is available for JVM.
 * The [SynchronizedObject] superclass gets erased (transformed to Any) on JVM and JS,
 * with `synchronized` leaving no trace in the code on JS and getting replaced with built-in monitors for locking on JVM.
 */
@InternalAPI
public actual typealias SynchronizedObject = Any

/**
 * [ReentrantLock] from kotlinx.atomicfu.locks
 *
 * [ReentrantLock] is designed for delegation. You write `val lock = reentrantLock()` to construct its instance and
 * use `lock/tryLock/unlock` functions or `lock.withLock { ... }` extension function similarly to
 * the way jucl.ReentrantLock is used on JVM. On JVM it is a typealias to the later class, erased on JS.
 */
@Suppress("NOTHING_TO_INLINE")
@InternalAPI
public actual class ReentrantLock {
    public actual inline fun lock() {}
    public actual inline fun tryLock(): Boolean = true
    public actual inline fun unlock() {}
}

/**
 * Creates a new [ReentrantLock] instance.
 */
@JsName(REENTRANT_LOCK)
@InternalAPI
public val Lock: ReentrantLock = ReentrantLock()

/**
 * Creates a new [ReentrantLock] instance.
 */
@Suppress("NOTHING_TO_INLINE")
@InternalAPI
public actual inline fun reentrantLock(): ReentrantLock = Lock

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
public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T = block()

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
public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T = block()

private const val REENTRANT_LOCK = "atomicfu\$reentrantLock"
