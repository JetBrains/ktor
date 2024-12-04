/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.websocket

/**
 * A WebSocket frame type.
 * @property controlFrame if this is control frame type
 * @property opcode - frame type id that is used to transport it
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameType)
 */
public enum class FrameType(public val controlFrame: Boolean, public val opcode: Int) {
    /**
     * A regular application level text frame.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameType.TEXT)
     */
    TEXT(false, 1),

    /**
     * A regular application level binary frame.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameType.BINARY)
     */
    BINARY(false, 2),

    /**
     * A low level close frame.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameType.CLOSE)
     */
    CLOSE(true, 8),

    /**
     * A low level ping frame.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameType.PING)
     */
    PING(true, 9),

    /**
     * A low level pong frame.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameType.PONG)
     */
    PONG(true, 0xa);

    public companion object {
        private val maxOpcode = entries.maxByOrNull { it.opcode }!!.opcode

        private val byOpcodeArray = Array(maxOpcode + 1) { op -> entries.singleOrNull { it.opcode == op } }

        /**
         * Finds [FrameType] instance by numeric [opcode].
         * @return a [FrameType] instance or `null` of the [opcode] value is not valid
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.FrameType.Companion.get)
         */
        public operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}
