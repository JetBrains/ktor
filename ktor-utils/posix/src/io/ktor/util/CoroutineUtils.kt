/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

@InternalAPI
public fun Dispatchers.createFixedThreadDispatcher(name: String, threads: Int): CloseableCoroutineDispatcher =
    MultiWorkerDispatcher(name, threads)

@OptIn(ObsoleteWorkersApi::class)
private val CLOSE_WORKER: Worker by lazy {
    Worker.start(name = "CLOSE_WORKER")
}

@OptIn(InternalAPI::class, ObsoleteWorkersApi::class)
private class MultiWorkerDispatcher(name: String, workersCount: Int) : CloseableCoroutineDispatcher() {
    private val closed = atomic(false)
    private val tasksQueue = Channel<Runnable>(Channel.UNLIMITED)

    @OptIn(ObsoleteWorkersApi::class)
    private val workers = Array(workersCount) { Worker.start(name = "$name-$it") }

    @OptIn(ObsoleteWorkersApi::class)
    private val futures = mutableListOf<Future<Unit>>()

    init {
        for (worker in workers) {
            worker.execute(TransferMode.SAFE, { }) {
                ThreadInfo.registerCurrentThread()
            }.consume()

            val element: Future<Unit> = worker.execute(TransferMode.SAFE, { this }) {
                it.workerRunLoop()
            }

            futures.add(element)
        }
    }

    private fun workerRunLoop() = runBlocking {
        kotlin.runCatching {
            for (task in tasksQueue) {
                task.run()
            }
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (closed.value) return

        val result = tasksQueue.trySendBlocking(block)
        if (result.isSuccess) return

        throw IllegalStateException("Fail to dispatch task", result.exceptionOrNull())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        CLOSE_WORKER.execute(TransferMode.SAFE, { this }) {
            it.tasksQueue.close()

            it.futures.forEach {
                it.consume()
            }

            it.futures.clear()

            it.workers.forEach { worker ->
                ThreadInfo.dropWorker(worker)
                kotlin.runCatching {
                    worker.requestTermination().consume()
                }
            }
        }
    }
}

@OptIn(ObsoleteWorkersApi::class)
private fun <T> Future<T>.consume() {
    consume { }
}
