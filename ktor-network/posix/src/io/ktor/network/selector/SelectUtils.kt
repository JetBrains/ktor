/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.network.selector

import kotlinx.coroutines.*

internal expect class SelectorHelper() {
    fun interest(event: EventInfo): Boolean

    fun start(scope: CoroutineScope): Job

    fun requestTermination()

    fun notifyClosed(descriptor: Int)
}

@Deprecated(
    "This will not be thrown since 2.0.0.",
    level = DeprecationLevel.ERROR
)
public class SocketError : IllegalStateException()
