/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.util.date.*
import kotlin.test.*

class ParserServerSetCookieTest {
    @Test
    fun testSimpleParse() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testSimpleParseCustomEncoding() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=RAW"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.RAW, parsed.encoding)
    }

    @Test
    fun testSimpleParseMissingEncoding() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testSimpleParseVersionAtStart() {
        val header = "\$Version=1; key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testParseWithQuotes() {
        val header = "key=\"aaa; bbb = ccc\"; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa; bbb = ccc", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testParseExpires() {
        val header = "SESSION=cart%3D%2523cl%26userId%3D%2523sid1; " +
            "Expires=Sat, 16 Jan 2016 13:43:28 GMT; HttpOnly; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        val expires = parsed.expires
        assertNotNull(expires)
        assertEquals(2016, expires.year)
        assertEquals(Month.JANUARY, expires.month)
        assertEquals(16, expires.dayOfMonth)
    }

    @Test
    fun testParseBase64() {
        val header = "SESSION=MTIzCg==; \$x-enc=BASE64_ENCODING"
        val parsed = parseServerSetCookieHeader(header)
        assertEquals("123\n", parsed.value)
    }

    @Test
    fun testMaxAge() {
        val header = "key=aaa; max-age=999"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa", parsed.value)
        assertEquals(999, parsed.maxAge)
    }

    @Test
    fun testMaxAgeNegative() {
        val header = "key=aaa; max-age=-1"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa", parsed.value)
        assertEquals(0, parsed.maxAge)
    }

    @Test
    fun testMaxAgeTooLong() {
        val header = "key=aaa; max-age=3153600000"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa", parsed.value)
        assertEquals(Int.MAX_VALUE, parsed.maxAge)
    }

    @Test
    fun testSlash() {
        val header = "384f8bdb/sessid=GLU787LwmQa9uLqnM7nWHzBm; path=/"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("384f8bdb/sessid", parsed.name)
        assertEquals("GLU787LwmQa9uLqnM7nWHzBm", parsed.value)
        assertEquals("/", parsed.path)
    }
}
