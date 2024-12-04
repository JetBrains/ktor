/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.internal.*
import kotlinx.coroutines.*

/**
 * Raised when the frame is bigger than allowed in a current WebSocket session.
 * @param frameSize size of received or posted frame that is too big
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameTooBigException)
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class FrameTooBigException(
    public val frameSize: Long
) : Exception(), CopyableThrowable<FrameTooBigException> {

    override val message: String
        get() = "Frame is too big: $frameSize"

    override fun createCopy(): FrameTooBigException = FrameTooBigException(frameSize).also {
        it.initCauseBridge(this)
    }
}
