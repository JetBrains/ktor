/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.network.selector

import io.ktor.network.interop.*
import io.ktor.network.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import platform.posix.*
import kotlin.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.*

@OptIn(InternalAPI::class)
internal actual class SelectorHelper {
    private val fdSetSize: Int

    @OptIn(ExperimentalForeignApi::class)
    actual constructor() : this(fdSetSize = fd_setsize())

    constructor(fdSetSize: Int) {
        this.fdSetSize = fdSetSize
    }

    private val wakeupSignal = SignalPoint()
    private val interestQueue = LockFreeMPSCQueue<EventInfo>()
    private val closeQueue = LockFreeMPSCQueue<Int>()

    private val wakeupSignalEvent = EventInfo(
        wakeupSignal.selectionDescriptor,
        SelectInterest.READ,
        Continuation(EmptyCoroutineContext) {
        }
    )

    actual fun interest(event: EventInfo): Boolean {
        if (interestQueue.addLast(event)) {
            wakeupSignal.signal()
            return true
        }

        return false
    }

    actual fun start(scope: CoroutineScope): Job {
        val job = scope.launch(CoroutineName("selector")) {
            selectionLoop()
        }

        job.invokeOnCompletion {
            cleanup()
        }

        return job
    }

    actual fun requestTermination() {
        interestQueue.close()
        closeQueue.close()
        wakeupSignal.signal()
    }

    private fun cleanup() {
        while (true) {
            val event = closeQueue.removeFirstOrNull() ?: break
            closeDescriptor(event)
        }
        wakeupSignal.close()
    }

    actual fun notifyClosed(descriptor: Int) {
        if (closeQueue.addLast(descriptor)) {
            wakeupSignal.signal()
        } else {
            closeDescriptor(descriptor)
        }
    }

    @OptIn(ExperimentalForeignApi::class, InternalAPI::class)
    private fun selectionLoop() {
        val readSet = select_create_fd_set()
        val writeSet = select_create_fd_set()
        val errorSet = select_create_fd_set()

        try {
            val completed = mutableSetOf<EventInfo>()
            val watchSet = mutableSetOf<EventInfo>()
            val closeSet = mutableSetOf<Int>()

            while (!interestQueue.isClosed) {
                watchSet.add(wakeupSignalEvent)
                var maxDescriptor = fillHandlers(watchSet, readSet, writeSet, errorSet)
                if (maxDescriptor == 0) continue

                maxDescriptor = max(maxDescriptor + 1, wakeupSignalEvent.descriptor + 1)

                try {
                    selector_pselect(maxDescriptor + 1, readSet, writeSet, errorSet).check()
                } catch (_: PosixException.BadFileDescriptorException) {
                    // Thrown if any of the descriptors was closed.
                    // This means the sets are undefined so do not rely on their contents.
                    watchSet.forEach { event ->
                        if (!isDescriptorValid(event.descriptor)) {
                            event.fail(IOException("Bad descriptor ${event.descriptor} for ${event.interest}"))
                            watchSet.remove(event)
                        }
                    }
                    continue
                }

                processSelectedEvents(watchSet, closeSet, completed, readSet, writeSet, errorSet)
            }

            val exception = CancellationException("Selector closed")
            while (!interestQueue.isEmpty) {
                interestQueue.removeFirstOrNull()?.fail(exception)
            }

            for (item in watchSet) {
                item.fail(exception)
            }
        } finally {
            selector_release_fd_set(readSet)
            selector_release_fd_set(writeSet)
            selector_release_fd_set(errorSet)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fillHandlers(
        watchSet: MutableSet<EventInfo>,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>,
        errorSet: CValue<selection_set>
    ): Int {
        var maxDescriptor = 0

        select_fd_clear(readSet)
        select_fd_clear(writeSet)
        select_fd_clear(errorSet)

        while (true) {
            val event = interestQueue.removeFirstOrNull() ?: break
            watchSet.add(event)
        }

        for (event in watchSet) {
            addInterest(event, readSet, writeSet, errorSet)
            maxDescriptor = max(maxDescriptor, event.descriptor)
        }

        return maxDescriptor
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun addInterest(
        event: EventInfo,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>,
        errorSet: CValue<selection_set>
    ) {
        val set = descriptorSetByInterestKind(event, readSet, writeSet)

        check(event.descriptor >= 0) {
            "File descriptor ${event.descriptor} is negative"
        }
        check(event.descriptor < fdSetSize) {
            "File descriptor ${event.descriptor} is larger or equal to FD_SETSIZE ($fdSetSize)"
        }

        select_fd_add(event.descriptor, set)
        select_fd_add(event.descriptor, errorSet)

        check(select_fd_isset(event.descriptor, set) != 0)
        check(select_fd_isset(event.descriptor, errorSet) != 0)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun processSelectedEvents(
        watchSet: MutableSet<EventInfo>,
        closeSet: MutableSet<Int>,
        completed: MutableSet<EventInfo>,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>,
        errorSet: CValue<selection_set>
    ) {
        while (true) {
            val event = closeQueue.removeFirstOrNull() ?: break
            closeSet.add(event)
        }

        for (event in watchSet) {
            if (event.descriptor in closeSet) {
                completed.add(event)
                continue
            }

            val set = descriptorSetByInterestKind(event, readSet, writeSet)

            if (select_fd_isset(event.descriptor, errorSet) != 0) {
                completed.add(event)
                event.fail(IOException("Fail to select descriptor ${event.descriptor} for ${event.interest}"))
                continue
            }

            if (select_fd_isset(event.descriptor, set) == 0) continue

            if (event.descriptor == wakeupSignal.selectionDescriptor) {
                wakeupSignal.check()
                continue
            }

            completed.add(event)
            event.complete()
        }

        for (descriptor in closeSet) {
            closeDescriptor(descriptor)
        }
        closeSet.clear()

        watchSet.removeAll(completed)
        completed.clear()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun descriptorSetByInterestKind(
        event: EventInfo,
        readSet: CValue<selection_set>,
        writeSet: CValue<selection_set>
    ): CValue<selection_set> = when (event.interest) {
        SelectInterest.READ -> readSet
        SelectInterest.WRITE -> writeSet
        SelectInterest.ACCEPT -> readSet
        SelectInterest.CONNECT -> writeSet
    }

    private fun closeDescriptor(descriptor: Int) {
        close(descriptor)
    }

    private fun isDescriptorValid(descriptor: Int): Boolean {
        return fcntl(descriptor, F_GETFL) != -1 || errno != EBADF
    }
}
