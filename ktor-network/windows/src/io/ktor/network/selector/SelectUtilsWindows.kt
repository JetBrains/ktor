/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal actual class SelectorHelper {
    private val wakeupSignal = SignalPoint()
    private val interestQueue = LockFreeMPSCQueue<EventInfo>()
    private val closeQueue = LockFreeMPSCQueue<Int>()
    private val allWsaEvents = ConcurrentMap<Int, COpaquePointer?>()

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
        val completed = mutableSetOf<EventInfo>()
        val watchSet = mutableSetOf<EventInfo>()
        val closeSet = mutableSetOf<Int>()

        while (!interestQueue.isClosed) {
            val wsaEvents = fillHandlers(watchSet)
            val index = memScoped {
                val length = wsaEvents.size + 1
                val wsaEventsWithWake = allocArray<CPointerVarOf<COpaquePointer>>(length).apply {
                    wsaEvents.forEachIndexed { index, wsaEvent ->
                        this[index] = wsaEvent
                    }
                    this[length - 1] = wakeupSignal.event
                }
                WSAWaitForMultipleEvents(
                    cEvents = length.convert(),
                    lphEvents = wsaEventsWithWake,
                    fWaitAll = 0,
                    dwTimeout = UInt.MAX_VALUE,
                    fAlertable = 0
                ).toInt().check()
            }

            processSelectedEvents(watchSet, closeSet, completed, index, wsaEvents)
        }

        val exception = CancellationException("Selector closed")
        while (!interestQueue.isEmpty) {
            interestQueue.removeFirstOrNull()?.fail(exception)
        }

        for (item in watchSet) {
            item.fail(exception)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fillHandlers(
        watchSet: MutableSet<EventInfo>
    ): List<COpaquePointer?> {
        while (true) {
            val event = interestQueue.removeFirstOrNull() ?: break
            watchSet.add(event)
        }

        return watchSet
            .groupBy { it.descriptor }
            .map { (descriptor, events) ->
                val wsaEvent = allWsaEvents.computeIfAbsent(descriptor) {
                    WSACreateEvent()
                }

                var lNetworkEvents = events.fold(0) { acc, event ->
                    acc or descriptorSetByInterestKind(event)
                }
                // Always add close event so selector gets notified on socket disconnect.
                lNetworkEvents = lNetworkEvents or FD_CLOSE

                WSAEventSelect(
                    s = descriptor.convert(),
                    hEventObject = wsaEvent,
                    lNetworkEvents = lNetworkEvents
                )

                wsaEvent
            }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun processSelectedEvents(
        watchSet: MutableSet<EventInfo>,
        closeSet: MutableSet<Int>,
        completed: MutableSet<EventInfo>,
        wsaIndex: Int,
        wsaEvents: List<COpaquePointer?>
    ) {
        while (true) {
            val event = closeQueue.removeFirstOrNull() ?: break
            closeSet.add(event)
        }

        watchSet.forEachIndexed { index, event ->
            if (event.descriptor in closeSet) {
                completed.add(event)
                return@forEachIndexed
            }
            val wsaEvent = wsaEvents[index]
            val networkEvents = memScoped {
                val networkEvents = alloc<WSANETWORKEVENTS>()
                WSAEnumNetworkEvents(event.descriptor.convert(), wsaEvent, networkEvents.ptr).check()
                networkEvents.lNetworkEvents
            }

            val set = descriptorSetByInterestKind(event)

            val isClosed = networkEvents and FD_CLOSE != 0

            if (networkEvents and set == 0 && !isClosed) {
                return@forEachIndexed
            }

            completed.add(event)
            event.complete()
        }

        if (wsaIndex == wsaEvents.lastIndex + 1) {
            wakeupSignal.check()
        }

        for (descriptor in closeSet) {
            closeDescriptor(descriptor)
        }
        closeSet.clear()

        watchSet.removeAll(completed)
        completed.clear()
    }

    private fun descriptorSetByInterestKind(
        event: EventInfo
    ): Int = when (event.interest) {
        SelectInterest.READ -> FD_READ
        SelectInterest.WRITE -> FD_WRITE
        SelectInterest.ACCEPT -> FD_ACCEPT
        SelectInterest.CONNECT -> FD_CONNECT
    }

    private fun closeDescriptor(descriptor: Int) {
        close(descriptor)
        allWsaEvents.remove(descriptor)?.let { wsaEvent ->
            WSACloseEvent(wsaEvent)
        }
    }
}
