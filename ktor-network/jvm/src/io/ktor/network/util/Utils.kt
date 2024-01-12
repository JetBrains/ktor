/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.contracts.*

/**
 * Infinite timeout in milliseconds.
 */
internal const val INFINITE_TIMEOUT_MS = Long.MAX_VALUE
internal class Timeout(
    private val name: String,
    private val timeoutMs: Long,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val onTimeout: suspend () -> Unit
) {

    private val lastActivityTime = atomic(0L)
    private val isStarted = atomic(false)

    private var workerJob = initTimeoutJob()

    fun start() {
        lastActivityTime.value = clock()
        isStarted.value = true
    }

    fun stop() {
        isStarted.value = false
    }

    fun finish() {
        workerJob?.cancel()
    }

    private fun initTimeoutJob(): Job? {
        if (timeoutMs == INFINITE_TIMEOUT_MS) return null
        return scope.launch(scope.coroutineContext + CoroutineName("Timeout $name")) {
            try {
                while (true) {
                    if (!isStarted.value) {
                        lastActivityTime.value = clock()
                    }
                    val remaining = lastActivityTime.value + timeoutMs - clock()
                    if (remaining <= 0 && isStarted.value) {
                        break
                    }

                    delay(remaining)
                }
                yield()
                onTimeout()
            } catch (cause: Throwable) {
                // no op
            }
        }
    }
}

/**
 * Starts timeout coroutine that will invoke [onTimeout] after [timeoutMs] of inactivity.
 * Use [Timeout] object to wrap code that can timeout or cancel this coroutine
 */
internal fun CoroutineScope.createTimeout(
    name: String = "",
    timeoutMs: Long,
    clock: () -> Long = { getTimeMillis() },
    onTimeout: suspend () -> Unit
): Timeout {
    return Timeout(name, timeoutMs, clock, this, onTimeout)
}

internal inline fun <T> Timeout?.withTimeout(block: () -> T): T {
    if (this == null) {
        return block()
    }

    start()
    return try {
        block()
    } finally {
        stop()
    }
}
