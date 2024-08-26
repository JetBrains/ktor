/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.utils.io.*
import kotlin.coroutines.*

@InternalAPI
public actual fun MDCContext(): CoroutineContext.Element = MDCContextElement

internal object MDCContextElement : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = MDCContextKey

    override fun toString(): String = "MDCContext"

    object MDCContextKey : CoroutineContext.Key<MDCContextElement>
}
