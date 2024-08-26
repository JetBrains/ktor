/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.*
import java.nio.*

public class SimpleFrameCollector {
    private var remaining: Int = 0
    private var buffer: ByteBuffer? = null
    private val maskBuffer = ByteBuffer.allocate(4)

    public val hasRemaining: Boolean
        get() = remaining > 0

    public fun start(length: Int, bb: ByteBuffer) {
        require(remaining == 0) { throw IllegalStateException("remaining should be 0") }

        remaining = length
        if (buffer == null || buffer!!.capacity() < length) {
            buffer = ByteBuffer.allocate(length)
        }
        buffer!!.clear()

        handle(bb)
    }

    public fun handle(bb: ByteBuffer) {
        remaining -= bb.moveTo(buffer!!, remaining)
    }

    public fun take(maskKey: Int?): ByteBuffer = buffer!!.run {
        flip()

        val view = slice()

        if (maskKey != null) {
            maskBuffer.clear()
            maskBuffer.asIntBuffer().put(maskKey)
            maskBuffer.clear()

            view.xor(maskBuffer)
        }

        buffer = null
        view.asReadOnlyBuffer()
    }
}
