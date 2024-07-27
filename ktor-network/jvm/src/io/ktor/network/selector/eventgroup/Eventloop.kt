/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import io.ktor.network.selector.*
import kotlinx.coroutines.*
import java.nio.channels.*

internal class Eventloop {
    val scope = newThreadContext(nThreads = 1).wrapInScope()

    fun run(): Job {
        return scope.launch { runLoop() }
    }

    private val taskQueue = ArrayDeque<Task<*>>()

    private val selector = Selector.open()

    fun close(cause: Throwable?) {
        taskQueue.forEach { it.continuation.cancel(cause) }
        selector.close()
    }

    private suspend fun runLoop() {
        while (true) {
            runAllPendingTasks()

            val n = selector.select(SELECTOR_TIMEOUT_MILLIS)
            yield()

            if (n == 0) {
                continue
            }

            val selectionKeys = selector.selectedKeys().iterator()
            while (selectionKeys.hasNext()) {
                val key = selectionKeys.next()
                selectionKeys.remove()

                try {
                    if (!key.isValid) continue
                    key.attachment.runTaskAndResumeContinuation(key)
                } catch (e: Throwable) {
                    key.channel().close()
                    key.attachment.cancel(e)
                }
            }
        }
    }

    private suspend fun runAllPendingTasks() {
        repeat(taskQueue.size) {
            taskQueue.removeFirst().runAndResume()
        }
    }

    internal fun <T> runOnLoop(body: suspend () -> T): CompletableDeferred<T> {
        val result = CompletableDeferred<T>()
        taskQueue.addLast(Task(result.toResumableCancellable(), body))
        return result
    }

    internal fun addInterest(selectable: Selectable, interest: Int): SelectionKey {
        val channel = selectable.channel
        val key = channel.keyFor(selector)
        selectable.interestOp(SelectInterest.byValue(interest), true)
        val ops = selectable.interestedOps

        if (key == null) {
            if (ops != 0) {
                channel.register(selector, ops, Attachment())
            }
        } else {
            if (key.interestOps() != ops) {
                key.interestOps(ops)
            }
        }
        return key
    }

    internal fun deleteInterest(selectable: Selectable, interest: Int) {
        val channel = selectable.channel
        val key = channel.keyFor(selector)
        selectable.interestOp(SelectInterest.byValue(interest), false)
        val ops = selectable.interestedOps

        if (key == null) {
            if (ops != 0) {
                channel.register(selector, ops, Attachment())
            }
        } else {
            if (key.interestOps() != ops) {
                key.interestOps(ops)
            }
        }
    }

    companion object {
        private const val SELECTOR_TIMEOUT_MILLIS = 20L
    }
}
