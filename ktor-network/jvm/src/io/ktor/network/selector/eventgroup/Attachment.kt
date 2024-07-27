/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector.eventgroup

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.channels.SelectionKey

internal inline val SelectionKey.attachment get() = attachment() as Attachment

/**
 * Attachment for SelectionKey
 * It contains task for each interest and allows to run them and resume the continuation
 */
internal class Attachment {
    private var acceptTask: Task<Any?>? = null
    private var readTask: Task<Any?>? = null
    private var writeTask: Task<Any?>? = null
    private var connectTask: Task<Any?>? = null

    suspend fun <T> runTask(interest: Int, task: suspend () -> T): T {
        return suspendCancellableCoroutine {
            @Suppress("UNCHECKED_CAST")
            setContinuationByInterest(interest, Task(it.toResumableCancellable(), task) as Task<Any?>)
        }
    }

    suspend fun runTaskAndResumeContinuation(key: SelectionKey) {
        when {
            key.isAcceptable -> acceptTask.runAndResume(SelectionKey.OP_ACCEPT)
            key.isReadable -> readTask.runAndResume(SelectionKey.OP_READ)
            key.isWritable -> writeTask.runAndResume(SelectionKey.OP_WRITE)
            key.isConnectable -> connectTask.runAndResume(SelectionKey.OP_CONNECT)
        }
    }

    private suspend fun Task<Any?>?.runAndResume(interest: Int) {
        val task = this ?: return
        setContinuationByInterest(interest, null)
        task.runAndResume()
    }

    private fun setContinuationByInterest(interest: Int, task: Task<Any?>?) {
        when (interest) {
            SelectionKey.OP_ACCEPT -> acceptTask = task
            SelectionKey.OP_READ -> readTask = task
            SelectionKey.OP_WRITE -> writeTask = task
            SelectionKey.OP_CONNECT -> connectTask = task
        }
    }

    fun cancel(cause: Throwable? = null) {
        acceptTask.cancel(cause)
        readTask.cancel(cause)
        writeTask.cancel(cause)
        connectTask.cancel(cause)
    }

    private fun Task<*>?.cancel(cause: Throwable? = null) {
        this?.continuation?.cancel(cause)
    }
}
