/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.selector

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.nio.channels.*
import java.nio.channels.spi.*
import kotlin.coroutines.*

public actual fun SelectorManager(dispatcher: CoroutineContext): SelectorManager = ActorSelectorManager(dispatcher)

/**
 * Selector manager is a service that manages NIO selectors and selection threads
 */
public actual interface SelectorManager : CoroutineScope, Closeable {
    /**
     * NIO selector provider
     */
    public val provider: SelectorProvider

    /**
     * Notifies the selector that selectable has been closed.
     */
    public actual fun notifyClosed(selectable: Selectable)

    /**
     * Suspends until [interest] is selected for [selectable]
     * May cause manager to allocate and run selector instance if not yet created.
     *
     * Only one selection is allowed per [interest] per [selectable] but you can
     * select for different interests for the same selectable simultaneously.
     * In other words you can select for read and write at the same time but should never
     * try to read twice for the same selectable.
     */
    public actual suspend fun select(selectable: Selectable, interest: SelectInterest)

    public actual companion object
}

/**
 * Creates a NIO entity via [create] and calls [setup] on it. If any exception happens then the entity will be closed
 * and an exception will be propagated.
 */
public inline fun <C : Closeable, R> SelectorManager.buildOrClose(
    create: SelectorProvider.() -> C,
    setup: C.() -> R
): R {
    while (true) {
        val result = create(provider)

        try {
            return setup(result)
        } catch (t: Throwable) {
            result.close()
            throw t
        }
    }
}

/**
 * Select interest kind
 * @property [flag] to be set in NIO selector
 */
@Suppress("KDocMissingDocumentation")
public actual enum class SelectInterest(public val flag: Int) {
    READ(SelectionKey.OP_READ),
    WRITE(SelectionKey.OP_WRITE),
    ACCEPT(SelectionKey.OP_ACCEPT),
    CONNECT(SelectionKey.OP_CONNECT);

    public actual companion object {
        public actual val AllInterests: Array<SelectInterest> = values()

        public val flags: IntArray = values().map { it.flag }.toIntArray()

        public val size: Int = values().size

        private val byFlag = values().associateBy { it.flag }

        public fun byValue(value: Int): SelectInterest = byFlag[value] ?: error("Unknown SelectInterest value: $value")
    }
}
