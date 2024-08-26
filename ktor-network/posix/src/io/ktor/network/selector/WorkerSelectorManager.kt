/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.network.selector

import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkerSelectorManager : SelectorManager {
    @OptIn(DelicateCoroutinesApi::class)
    private val selectorContext = newSingleThreadContext("WorkerSelectorManager")
    private val job = Job()
    override val coroutineContext: CoroutineContext = selectorContext + job

    private val selector = SelectorHelper()

    init {
        selector.start(this)
    }

    override fun notifyClosed(selectable: Selectable) {
        selector.notifyClosed(selectable.descriptor)
    }

    override suspend fun select(
        selectable: Selectable,
        interest: SelectInterest
    ) {
        return suspendCancellableCoroutine { continuation ->
            val selectorState = EventInfo(selectable.descriptor, interest, continuation)
            if (!selector.interest(selectorState)) {
                continuation.resumeWithException(CancellationException("Selector closed."))
            }
        }
    }

    override fun close() {
        selector.requestTermination()
        selectorContext.close()
        job.cancel()
    }
}
