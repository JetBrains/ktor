/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.selector

import kotlin.coroutines.*

internal data class EventInfo(
    val descriptor: Int,
    val interest: SelectInterest,
    private val continuation: Continuation<Unit>
) {

    fun complete() {
        continuation.resume(Unit)
    }

    fun fail(cause: Throwable) {
        continuation.resumeWithException(cause)
    }

    override fun toString(): String = "EventInfo[$descriptor, $interest]"
}
