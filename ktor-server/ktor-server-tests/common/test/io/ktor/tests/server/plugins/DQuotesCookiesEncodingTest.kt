/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import kotlin.test.*

class DQuotesCookiesEncodingTest {
    @Test
    fun no_bad_characters() {
        testEncode("abc", "abc")
    }

    @Test
    fun space_inside() {
        testEncode("\"abc 123\"", "abc 123")
    }

    @Test
    fun equals_inside() {
        testEncode("abc=123", "abc=123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.DQUOTES)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.DQUOTES), "Decode failed")
    }
}
