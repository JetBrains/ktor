/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.selector

import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import java.nio.channels.*
import java.nio.channels.spi.*
import kotlin.coroutines.*

/**
 * Base class for NIO selector managers
 */
public abstract class SelectorManagerSupport internal constructor() : SelectorManager {
    public final override val provider: SelectorProvider = SelectorProvider.provider()

    /**
     * Number of pending selectable.
     */
    protected var pending: Int = 0

    /**
     * Number of cancelled keys.
     */
    protected var cancelled: Int = 0

    /**
     * Publish current [selectable] interest, any thread
     */
    protected abstract fun publishInterest(selectable: Selectable)

    public final override suspend fun select(selectable: Selectable, interest: SelectInterest) {
        val interestedOps = selectable.interestedOps
        val flag = interest.flag

        if (selectable.isClosed) selectableIsClosed()
        if (interestedOps and flag == 0) selectableIsInvalid(interestedOps, flag)

        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation {
                // TODO: We've got a race here (and exception erasure)!
            }
            selectable.suspensions.addSuspension(interest, continuation)

            if (!continuation.isCancelled) {
                publishInterest(selectable)
            }
        }
    }

    /**
     * Handle selected keys clearing [selectedKeys] set
     */
    protected fun handleSelectedKeys(selectedKeys: MutableSet<SelectionKey>, keys: Set<SelectionKey>) {
        val selectedCount = selectedKeys.size
        pending = keys.size - selectedCount
        cancelled = 0

        if (selectedCount <= 0) return
        val iter = selectedKeys.iterator()
        while (iter.hasNext()) {
            val k = iter.next()
            handleSelectedKey(k)
            iter.remove()
        }
    }

    /**
     * Handles particular selected key
     */
    protected fun handleSelectedKey(key: SelectionKey) {
        try {
            val readyOps = key.readyOps()
            val interestOps = key.interestOps()

            val subject = key.subject
            if (subject == null) {
                key.cancel()
                cancelled++
            } else {
                subject.suspensions.invokeForEachPresent(readyOps) { resume(Unit) }

                val newOps = interestOps and readyOps.inv()
                if (newOps != interestOps) {
                    key.interestOps(newOps)
                }

                if (newOps != 0) {
                    pending++
                }
            }
        } catch (cause: Throwable) {
            // cancelled or rejected on resume?
            key.cancel()
            cancelled++
            key.subject?.let { subject ->
                cancelAllSuspensions(subject, cause)
                key.subject = null
            }
        }
    }

    /**
     * Applies selectable's current interest (should be invoked in selection thread)
     */
    protected fun applyInterest(selector: Selector, selectable: Selectable) {
        try {
            val channel = selectable.channel
            val key = channel.keyFor(selector)
            val ops = selectable.interestedOps

            if (key == null) {
                if (ops != 0) {
                    channel.register(selector, ops, selectable)
                }
            } else {
                if (key.interestOps() != ops) {
                    key.interestOps(ops)
                }
            }

            if (ops != 0) {
                pending++
            }
        } catch (cause: Throwable) {
            selectable.channel.keyFor(selector)?.cancel()
            cancelAllSuspensions(selectable, cause)
        }
    }

    /**
     * Notify selectable's closure
     */
    protected fun notifyClosedImpl(selector: Selector, key: SelectionKey, attachment: Selectable) {
        cancelAllSuspensions(attachment, ClosedChannelException())

        key.subject = null
        selector.wakeup()
    }

    /**
     * Cancel all selectable's suspensions with the specified exception
     */
    protected fun cancelAllSuspensions(attachment: Selectable, cause: Throwable) {
        attachment.suspensions.invokeForEachPresent {
            resumeWithException(cause)
        }
    }

    /**
     * Cancel all suspensions with the specified exception, reset all interests
     */
    protected fun cancelAllSuspensions(selector: Selector, cause: Throwable?) {
        val currentCause = cause ?: ClosedSelectorCancellationException()

        selector.keys().forEach { key ->
            try {
                if (key.isValid) key.interestOps(0)
            } catch (ignore: CancelledKeyException) {
            }
            (key.attachment() as? Selectable)?.let { cancelAllSuspensions(it, currentCause) }
            key.cancel()
        }
    }

    private var SelectionKey.subject: Selectable?
        get() = attachment() as? Selectable
        set(newValue) {
            attach(newValue)
        }

    public class ClosedSelectorCancellationException : CancellationException("Closed selector")
}

private fun selectableIsClosed(): Nothing {
    throw IOException("Selectable is already closed")
}

private fun selectableIsInvalid(interestedOps: Int, flag: Int): Nothing {
    error("Selectable is invalid state: $interestedOps, $flag")
}
