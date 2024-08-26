/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class HeadersJvmTest {
    private val ch = ByteChannel(true)
    private val builder = CharArrayBuilder()

    @OptIn(InternalAPI::class)
    @AfterTest
    fun tearDown() {
        ch.close()
        builder.release()
    }

    @Test
    fun smokeTest() = runBlocking {
        ch.writeStringUtf8("Host: localhost\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        assertEquals("localhost", hh["host"]?.toString())
        assertEquals("localhost", hh["hOst"]?.toString())
        assertEquals("localhost", hh["HOST"]?.toString())
        assertNull(hh["Host "])

        hh.release()
    }

    @Test
    fun smokeTestUnicode() = runBlocking {
        ch.writeStringUtf8("Host: unicode-\u0422\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("unicode-\u0422", hh["Host"]?.toString())

        hh.release()
    }

    @Test
    fun extraSpacesLeading(): Unit = runBlocking<Unit> {
        ch.writeStringUtf8(" Host:  localhost\r\n\r\n")
        assertFailsWith<ParserException> {
            parseHeaders(ch, builder)!!.release()
        }
    }

    @Test
    fun extraSpacesMiddle(): Unit = runBlocking {
        ch.writeStringUtf8("Host:  localhost\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun extraSpacesMiddleBeforeColon(): Unit = runBlocking<Unit> {
        ch.writeStringUtf8("Host : localhost\r\n\r\n")
        assertFailsWith<ParserException> {
            parseHeaders(ch, builder)!!.release()
        }
    }

    @Test
    fun extraSpacesMiddleBeforeColonNoAfter(): Unit = runBlocking<Unit> {
        ch.writeStringUtf8("Host :localhost\r\n\r\n")
        assertFailsWith<ParserException> {
            parseHeaders(ch, builder)!!.release()
        }
    }

    @Test
    fun extraSpacesTrailing() = runBlocking {
        ch.writeStringUtf8("Host:  localhost \r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun alternativeLineSeparatorsFirst() = runBlocking {
        ch.writeStringUtf8("Host: localhost\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun alternativeLineSeparatorsSecond() = runBlocking {
        ch.writeStringUtf8("Host: localhost\n\n\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun alternativeLineSeparatorsBoth() = runBlocking {
        ch.writeStringUtf8("Host: localhost\n\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun testExpectHttpBodyGet() = runBlocking {
        ch.writeStringUtf8("GET / HTTP/1.1\nConnection: close\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyGetAndContentLength() = runBlocking {
        ch.writeStringUtf8("GET / HTTP/1.1\nContent-Length: 0\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyGetAndContentLengthNonZero() = runBlocking {
        ch.writeStringUtf8("GET / HTTP/1.1\nContent-Length: 10\n\n")
        val request = parseRequest(ch)!!

        try {
            assertTrue { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostContentLengthZero() = runBlocking {
        ch.writeStringUtf8("POST / HTTP/1.1\nContent-Length: 0\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostContentLengthNonZero() = runBlocking {
        ch.writeStringUtf8("POST / HTTP/1.1\nContent-Length: 10\n\n")
        val request = parseRequest(ch)!!

        try {
            assertTrue { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostContentChunked() = runBlocking {
        ch.writeStringUtf8("POST / HTTP/1.1\nTransfer-Encoding: chunked\n\n")
        val request = parseRequest(ch)!!

        try {
            assertTrue { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostOnly() = runBlocking {
        ch.writeStringUtf8("POST / HTTP/1.1\nX: 0\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testEmptyHeaderValue() = runBlocking {
        ch.writeStringUtf8("Host:\r\n\r\n")
        val headers = parseHeaders(ch, builder)!!
        assertEquals("", headers["Host"]?.toString())

        headers.release()
    }

    @Test
    fun testNoColon(): Unit = runBlocking<Unit> {
        ch.writeStringUtf8("Host\r\n\r\n")

        assertFails {
            runBlocking {
                parseHeaders(ch, builder)
            }
        }
    }

    @Test
    fun testBlankHeaderValue() = runBlocking {
        ch.writeStringUtf8("Host: \r\n\r\n")
        val headers = parseHeaders(ch, builder)!!
        assertEquals("", headers["Host"]?.toString())

        headers.release()
    }

    @Test
    fun testWrongHeader() = runBlocking<Unit> {
        ch.writeStringUtf8("Hello world\r\n\r\n")

        assertFails {
            runBlocking {
                parseHeaders(ch, builder)
            }
        }
    }

    @Test
    fun `Host header with invalid character (slash)`() = runBlocking<Unit> {
        ch.writeStringUtf8("Host: www/exam/ple.com\n\n")

        assertFailsWith<IllegalStateException> {
            parseHeaders(ch, builder)
        }
    }

    @Test
    fun `Host header with invalid character (question mark)`() = runBlocking<Unit> {
        ch.writeStringUtf8("Host: www.example?com\n\n")

        assertFailsWith<IllegalStateException> {
            parseHeaders(ch, builder)
        }
    }

    @Test
    fun `Host header with invalid '#' character`() = runBlocking<Unit> {
        ch.writeStringUtf8("Host: www.ex#mple.com\n\n")

        assertFailsWith<IllegalStateException> {
            parseHeaders(ch, builder)
        }
    }

    @Test
    fun `Host header with invalid '@' character`() = runBlocking<Unit> {
        ch.writeStringUtf8("Host: www.ex@mple.com\n\n")

        assertFailsWith<IllegalStateException> {
            parseHeaders(ch, builder)
        }
    }
}
