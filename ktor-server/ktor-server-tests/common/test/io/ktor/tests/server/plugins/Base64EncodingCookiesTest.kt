/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import kotlin.test.*

class Base64EncodingCookiesTest {
    @Test
    fun no_bad_characters() {
        testEncode("YWJj", "abc")
    }

    @Test
    fun space_inside() {
        testEncode("YWJjIDEyMw==", "abc 123")
    }

    @Test
    fun equals_inside() {
        testEncode("YWJjPTEyMw==", "abc=123")
    }

    private fun testEncode(expected: String, value: String): String {
        val encoded = encodeCookieValue(value, CookieEncoding.BASE64_ENCODING)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.BASE64_ENCODING), "Decode failed")
        return encoded
    }
}
