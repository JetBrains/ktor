/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import kotlin.test.*

class SessionTransportTransformerKtTest {
    @Test
    fun allTransformersShouldBeAppliedOnWrite() {
        assertEquals("0;1;2", listOf(Appending("1"), Appending("2")).transformWrite("0"))
    }

    @Test
    fun allTransformersShouldBeAppliedOnReadInReverseOrder() {
        assertEquals("0", listOf(Appending("1"), Appending("2")).transformRead("0;1;2"))
    }

    private class Appending(val value: String) : SessionTransportTransformer {
        companion object {
            const val separator = ';'
        }

        override fun transformRead(transportValue: String): String? {
            return transportValue
                .substringBeforeLast(separator)
                .takeIf { transportValue.substringAfterLast(separator) == value }
        }

        override fun transformWrite(transportValue: String): String {
            return "$transportValue$separator$value"
        }
    }
}
