/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@InternalAPI
public actual fun SelectorManager(
    dispatcher: CoroutineContext
): SelectorManager = WorkerSelectorManager()

public actual interface SelectorManager : CoroutineScope, Closeable {
    /**
     * Notifies the selector that selectable has been closed.
     */
    public actual fun notifyClosed(s: Selectable)

    /**
     * Suspends until [interest] is selected for [selectable]
     * May cause manager to allocate and run selector instance if not yet created.
     *
     * Only one selection is allowed per [interest] per [selectable] but you can
     * select for different interests for the same selectable simultaneously.
     * In other words you can select for read and write at the same time but should never
     * try to read twice for the same selectable.
     */
    public actual suspend fun select(
        selectable: Selectable,
        interest: SelectInterest
    )

    public actual companion object
}

/**
 * Select interest kind
 * @property [flag] to be set in NIO selector
 */
@Suppress("KDocMissingDocumentation", "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING")
@KtorExperimentalAPI
@InternalAPI
public actual enum class SelectInterest {
    READ, WRITE, ACCEPT, CONNECT;

    public actual companion object {
        actual val AllInterests: Array<SelectInterest>
            get() = values()
    }

}
