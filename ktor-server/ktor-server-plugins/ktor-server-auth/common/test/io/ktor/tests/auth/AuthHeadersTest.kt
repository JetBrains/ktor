/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import kotlin.test.*

class AuthHeadersTest {
    @Test
    fun testWithParameterParameterBehaviour() {
        val header = HttpAuthHeader.Parameterized("authScheme", mapOf("a" to "a"))
        assertEquals("a", header.withParameter("a", "A").parameter("a"), "The first value should be returned")
        assertEquals("B", header.withParameter("b", "B").parameter("b"))
    }

    @Test
    fun testWithParameterPreservesEncoding() {
        for (encoding in listOf(HeaderValueEncoding.QUOTED_ALWAYS, HeaderValueEncoding.URI_ENCODE)) {
            val header = HttpAuthHeader.Parameterized("authScheme", mapOf("a" to "b"), encoding)
            assertEquals(header.encoding, header.withParameter("a", "c").encoding)
        }
    }

    @Test
    fun testReplaceParameter() {
        HttpAuthHeader.Parameterized("testScheme", linkedMapOf("a" to "2")).let { headerWithNoSuchEntires ->
            headerWithNoSuchEntires.withReplacedParameter("X", "1").let { result ->
                assertNotSame(headerWithNoSuchEntires, result)
                assertEquals(2, result.parameters.size)
                assertEquals("1", result.parameters.single { it.name == "X" }.value)
                assertEquals("2", result.parameters.single { it.name == "a" }.value)
            }
        }
        HttpAuthHeader.Parameterized("testScheme", linkedMapOf("a" to "2", "X" to "0"))
            .let { headerWithSingleOccurrence ->
                headerWithSingleOccurrence.withReplacedParameter("X", "1").let { result ->
                    assertNotSame(headerWithSingleOccurrence, result)
                    assertEquals(2, result.parameters.size)
                    assertEquals("1", result.parameters.single { it.name == "X" }.value)
                    assertEquals("2", result.parameters.single { it.name == "a" }.value)
                }
            }
        HttpAuthHeader.Parameterized("testScheme", linkedMapOf("a" to "2", "X" to "0", "X" to "3"))
            .let { headerWithMultipleOccurrences ->
                headerWithMultipleOccurrences.withReplacedParameter("X", "1").let { result ->
                    assertNotSame(headerWithMultipleOccurrences, result)
                    assertEquals(2, result.parameters.size)
                    assertEquals("1", result.parameters.single { it.name == "X" }.value)
                    assertEquals("2", result.parameters.single { it.name == "a" }.value)
                }
            }
        HttpAuthHeader.Parameterized("testScheme", linkedMapOf("a" to "2", "X" to "0", "m" to "2", "X" to "7"))
            .let { inTheMiddleReplace ->
                inTheMiddleReplace.withReplacedParameter("X", "1").let { result ->
                    assertNotSame(inTheMiddleReplace, result)
                    assertEquals(3, result.parameters.size)
                    assertEquals("1", result.parameters.single { it.name == "X" }.value)
                    assertEquals("2", result.parameters.single { it.name == "a" }.value)
                    assertEquals(1, result.parameters.indexOfLast { it.name == "X" })
                }
            }
    }

    @Test
    fun testInvalidTokenIsNotInException() {
        assertFails {
            val headers = Headers.build { append(HttpHeaders.Authorization, "Bearer invalid@token") }
            headers.parseAuthorizationHeader()
        }.let { cause ->
            assertFalse(cause.message!!.contains("token"))
            assertFalse(cause.cause!!.message!!.contains("token"))
        }

        assertFails {
            val headers = Headers.build { append(HttpHeaders.Authorization, "Bearer invalid token") }
            headers.parseAuthorizationHeader()
        }.let { cause ->
            assertFalse(cause.message!!.contains("token"))
            assertFalse(cause.cause!!.message!!.contains("token"))
        }

        assertFails {
            val headers = Headers.build { append(HttpHeaders.Authorization, "Bearer invalid\"token") }
            headers.parseAuthorizationHeader()
        }.let { cause ->
            assertFalse(cause.message!!.contains("token"))
            assertFalse(cause.cause!!.message!!.contains("token"))
        }

        assertFails {
            val headers = Headers.build { append(HttpHeaders.Authorization, "Bearer invâlid-token") }
            headers.parseAuthorizationHeader()
        }.let { cause ->
            assertFalse(cause.message!!.contains("token"))
            assertFalse(cause.cause!!.message!!.contains("token"))
        }
    }
}
