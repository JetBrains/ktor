package io.ktor.network.selector

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Creates the selector manager for current platform.
 */
@Suppress("FunctionName")
public expect fun SelectorManager(
    dispatcher: CoroutineContext = EmptyCoroutineContext
): SelectorManager

/**
 * SelectorManager interface allows [Selectable] wait for [SelectInterest].
 */
public expect interface SelectorManager : CoroutineScope, Closeable {
    /**
     * Notifies the selector that selectable has been closed.
     */
    public fun notifyClosed(selectable: Selectable)

    /**
     * Suspends until [interest] is selected for [selectable]
     * May cause manager to allocate and run selector instance if not yet created.
     *
     * Only one selection is allowed per [interest] per [selectable] but you can
     * select for different interests for the same selectable simultaneously.
     * In other words you can select for read and write at the same time but should never
     * try to read twice for the same selectable.
     */
    public suspend fun select(selectable: Selectable, interest: SelectInterest)

    public companion object
}

/**
 * Select interest kind.
 */
public expect enum class SelectInterest {
    READ, WRITE, ACCEPT, CONNECT;

    public companion object {
        public val AllInterests: Array<SelectInterest>
    }
}
