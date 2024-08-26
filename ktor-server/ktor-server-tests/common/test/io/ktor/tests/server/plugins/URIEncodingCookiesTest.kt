/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import kotlin.test.*

class URIEncodingCookiesTest {
    @Test
    fun no_bad_characters() {
        testEncode("abc", "abc")
    }

    @Test
    fun space_inside() {
        testEncode("abc+123", "abc 123")
    }

    @Test
    fun equals_inside() {
        testEncode("abc%3D123", "abc=123")
    }

    @Test
    fun encode_keep_digits() {
        testEncode("0123456789", "0123456789")
    }

    @Test
    fun encode_keep_letters() {
        testEncode(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        )
    }

    @Test
    fun encode_keep_hypen() {
        testEncode("abc-123", "abc-123")
    }

    @Test
    fun encode_keep_underscore() {
        testEncode("abc_123", "abc_123")
    }

    @Test
    fun encode_keep_period() {
        testEncode("abc.123", "abc.123")
    }

    @Test
    fun encode_keep_tilde() {
        testEncode("abc~123", "abc~123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.URI_ENCODING)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.URI_ENCODING), "Decode failed")
    }
}
