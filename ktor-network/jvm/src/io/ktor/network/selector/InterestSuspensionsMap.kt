/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.coroutines.*
import java.util.concurrent.atomic.*

public class InterestSuspensionsMap {
    @Volatile
    private var readHandlerReference: CancellableContinuation<Unit>? = null

    @Volatile
    private var writeHandlerReference: CancellableContinuation<Unit>? = null

    @Volatile
    private var connectHandlerReference: CancellableContinuation<Unit>? = null

    @Volatile
    private var acceptHandlerReference: CancellableContinuation<Unit>? = null

    public fun addSuspension(interest: SelectInterest, continuation: CancellableContinuation<Unit>) {
        val updater = updater(interest)

        if (!updater.compareAndSet(this, null, continuation)) {
            error("Handler for ${interest.name} is already registered")
        }
    }

    public inline fun invokeForEachPresent(readyOps: Int, block: CancellableContinuation<Unit>.() -> Unit) {
        val flags = SelectInterest.flags

        for (ordinal in flags.indices) {
            if (flags[ordinal] and readyOps != 0) {
                removeSuspension(ordinal)?.block()
            }
        }
    }

    public inline fun invokeForEachPresent(block: CancellableContinuation<Unit>.(SelectInterest) -> Unit) {
        for (interest in SelectInterest.AllInterests) {
            removeSuspension(interest)?.run { block(interest) }
        }
    }

    public fun removeSuspension(interest: SelectInterest): CancellableContinuation<Unit>? =
        updater(interest).getAndSet(this, null)

    public fun removeSuspension(interestOrdinal: Int): CancellableContinuation<Unit>? =
        updaters[interestOrdinal].getAndSet(this, null)

    override fun toString(): String {
        return "R $readHandlerReference W $writeHandlerReference C $connectHandlerReference A $acceptHandlerReference"
    }

    public companion object {
        @Suppress("UNCHECKED_CAST")
        private val updaters = SelectInterest.AllInterests.map { interest ->
            val property = when (interest) {
                SelectInterest.READ -> InterestSuspensionsMap::readHandlerReference
                SelectInterest.WRITE -> InterestSuspensionsMap::writeHandlerReference
                SelectInterest.ACCEPT -> InterestSuspensionsMap::acceptHandlerReference
                SelectInterest.CONNECT -> InterestSuspensionsMap::connectHandlerReference
            }
            AtomicReferenceFieldUpdater.newUpdater(
                InterestSuspensionsMap::class.java,
                CancellableContinuation::class.java,
                property.name
            ) as AtomicReferenceFieldUpdater<InterestSuspensionsMap, CancellableContinuation<Unit>?>
        }.toTypedArray()

        private fun updater(
            interest: SelectInterest
        ): AtomicReferenceFieldUpdater<InterestSuspensionsMap, CancellableContinuation<Unit>?> =
            updaters[interest.ordinal]
    }
}
