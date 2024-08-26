/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.websocket

import io.ktor.utils.io.*
import kotlin.jvm.*

/**
 * A WebSocket close reason.
 * @property code - close reason code as per RFC 6455, recommended to be one of [CloseReason.Codes]
 * @property message - a close reason message, could be empty
 */
public data class CloseReason(val code: Short, val message: String) {
    public constructor(code: Codes, message: String) : this(code.code, message)

    /**
     * An enum value for this [code] or `null` if the [code] is not listed in [Codes]
     */
    val knownReason: Codes?
        get() = Codes.byCode(code)

    override fun toString(): String {
        return "CloseReason(reason=${knownReason ?: code}, message=$message)"
    }

    /**
     * Standard close reason codes
     *
     * see https://tools.ietf.org/html/rfc6455#section-7.4 for list of codes
     */
    public enum class Codes(public val code: Short) {
        NORMAL(1000),
        GOING_AWAY(1001),
        PROTOCOL_ERROR(1002),
        CANNOT_ACCEPT(1003),

        @InternalAPI
        @Deprecated("This code MUST NOT be set as a status code in a Close control frame by an endpoint")
        CLOSED_ABNORMALLY(1006),
        NOT_CONSISTENT(1007),
        VIOLATED_POLICY(1008),
        TOO_BIG(1009),
        NO_EXTENSION(1010),
        INTERNAL_ERROR(1011),
        SERVICE_RESTART(1012),
        TRY_AGAIN_LATER(1013);

        public companion object {
            private val byCodeMap = entries.associateBy { it.code }

            @Deprecated(
                "Use INTERNAL_ERROR instead.",
                ReplaceWith(
                    "INTERNAL_ERROR",
                    "io.ktor.websocket.CloseReason.Codes.INTERNAL_ERROR"
                ),
                level = DeprecationLevel.ERROR
            )
            @JvmField
            @Suppress("UNUSED")
            public val UNEXPECTED_CONDITION: Codes = INTERNAL_ERROR

            /**
             * Get enum value by close reason code
             * @return enum instance or null if [code] is not in standard
             */
            public fun byCode(code: Short): Codes? = byCodeMap[code]
        }
    }
}
