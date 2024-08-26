/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.http.auth.*
import io.ktor.utils.io.*
import kotlin.random.*
import kotlin.test.*

class AuthorizeHeaderParserTest {
    @Test
    fun empty() {
        testParserParameterized("Basic", emptyMap(), "Basic")
    }

    @Test
    fun emptyWithTrailingSpaces() {
        testParserParameterized("Basic", emptyMap(), "Basic ")
    }

    @Test
    fun singleSimple() {
        testParserSingle("Basic", "abc==", "Basic abc==")
    }

    @Test
    fun testParameterizedSimple() {
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a=1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a =1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a = 1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a= 1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic  a=1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a=1 ")
    }

    @Test
    fun testParameterizedSimpleTwoParams() {
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1, b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1,b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 ,b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 , b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 , b=2 ")
    }

    @Test
    fun testParameterizedQuoted() {
        testParserParameterized("Basic", mapOf("a" to "1 2"), "Basic a=\"1 2\"")
    }

    @Test
    fun testParameterizedQuotedEscaped() {
        testParserParameterized("Basic", mapOf("a" to "1 \" 2"), "Basic a=\"1 \\\" 2\"")
        testParserParameterized("Basic", mapOf("a" to "1 A 2"), "Basic a=\"1 \\A 2\"")
    }

    @Test
    fun testParameterizedQuotedEscapedInTheMiddle() {
        testParserParameterized("Basic", mapOf("a" to "1 \" 2", "b" to "2"), "Basic a=\"1 \\\" 2\", b= 2")
    }

    @Test
    fun testMultipleChallengesParameters() {
        val expected = listOf(
            HttpAuthHeader.Parameterized("Digest", emptyMap()),
            HttpAuthHeader.Parameterized("Bearer", mapOf("1" to "2", "3" to "4")),
            HttpAuthHeader.Parameterized("Basic", emptyMap()),
        )
        testParserMultipleChallenges(expected, "Digest, Bearer 1 = 2, 3=4, Basic ")
    }

    @Test
    fun testMultipleChallengesSingle() {
        val expected = listOf(
            HttpAuthHeader.Single("Bearer", "abc=="),
            HttpAuthHeader.Parameterized("Bearer", mapOf("abc" to "def")),
            HttpAuthHeader.Single("Basic", "def==="),
            HttpAuthHeader.Parameterized("Digest", emptyMap())
        )
        testParserMultipleChallenges(expected, "Bearer abc==, Bearer abc=def, Basic def===, Digest")
    }

    @Test
    fun testMultipleChallengesAllHeaders() {
        val expected = listOf(
            HttpAuthHeader.Parameterized("Basic", emptyMap()),
            HttpAuthHeader.Parameterized("Bearer", mapOf("abc" to "def")),
            HttpAuthHeader.Single("Digest", "abc==")
        )
        testParserMultipleChallenges(expected, "Basic, Bearer abc=def,Digest abc==")
    }

    private fun testParserSingle(scheme: String, value: String, headerValue: String) {
        val actual = parseAuthorizationHeader(headerValue)!!

        assertEquals(scheme, actual.authScheme)

        if (actual is HttpAuthHeader.Single) {
            assertEquals(value, actual.blob)
        } else {
            fail("It should return single-value credential")
        }
    }

    private fun testParserParameterized(scheme: String, value: Map<String, String>, headerValue: String) {
        val actual = parseAuthorizationHeader(headerValue)!!

        assertEquals(scheme, actual.authScheme)

        if (actual is HttpAuthHeader.Parameterized) {
            assertEquals(value, actual.parameters.associateBy({ it.name }, { it.value }))
        } else {
            fail("It should return parameterized-value credential")
        }
    }

    @OptIn(InternalAPI::class)
    private fun testParserMultipleChallenges(expected: List<HttpAuthHeader>, headerValue: String) {
        val actual = parseAuthorizationHeaders(headerValue)

        assertEquals(expected.size, actual.size)
        (expected zip actual).forEach { (expectedHeader, actualHeader) ->
            if (expectedHeader is HttpAuthHeader.Single) {
                assertIs<HttpAuthHeader.Single>(actualHeader)

                assertEquals(expectedHeader.blob, actualHeader.blob)
            }
            if (expectedHeader is HttpAuthHeader.Parameterized) {
                assertIs<HttpAuthHeader.Parameterized>(actualHeader)
                assertEquals(
                    expectedHeader.parameters.associateBy({ it.name }, { it.value }),
                    actualHeader.parameters.associateBy({ it.name }, { it.value })
                )
            }
        }
    }

    private fun Random.nextString(
        length: Int,
        possible: Iterable<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    ) = possible.toList().let { possibleElements ->
        (0 until length).map { nextFrom(possibleElements) }.joinToString("")
    }

    private fun Random.nextString(length: Int, possible: String) = nextString(length, possible.toList())

    private fun <T> Random.nextFrom(possibleElements: List<T>): T =
        if (possibleElements.isEmpty()) {
            throw NoSuchElementException()
        } else {
            possibleElements[nextInt(possibleElements.size)]
        }
}
