/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.internal.*
import kotlinx.coroutines.*

/**
 * Raised when peers send frames which violate the Websocket RFC
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.ProtocolViolationException)
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ProtocolViolationException(
    public val violation: String
) : Exception(), CopyableThrowable<ProtocolViolationException> {
    override val message: String
        get() = "Received illegal frame: $violation"

    override fun createCopy(): ProtocolViolationException = ProtocolViolationException(violation).also {
        it.initCauseBridge(this)
    }
}
