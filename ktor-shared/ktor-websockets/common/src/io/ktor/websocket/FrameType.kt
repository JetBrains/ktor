/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.websocket

/**
 * A WebSocket frame type.
 * @property controlFrame if this is control frame type
 * @property opcode - frame type id that is used to transport it
 */
public enum class FrameType(public val controlFrame: Boolean, public val opcode: Int) {
    /**
     * A regular application level text frame.
     */
    TEXT(false, 1),

    /**
     * A regular application level binary frame.
     */
    BINARY(false, 2),

    /**
     * A low level close frame.
     */
    CLOSE(true, 8),

    /**
     * A low level ping frame.
     */
    PING(true, 9),

    /**
     * A low level pong frame.
     */
    PONG(true, 0xa);

    public companion object {
        private val maxOpcode = entries.maxByOrNull { it.opcode }!!.opcode

        private val byOpcodeArray = Array(maxOpcode + 1) { op -> entries.singleOrNull { it.opcode == op } }

        /**
         * Finds [FrameType] instance by numeric [opcode].
         * @return a [FrameType] instance or `null` of the [opcode] value is not valid
         */
        public operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}
