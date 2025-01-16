/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class UrlEncodedParametersBuilderTest {

    @Test
    fun testMethodsDelegateToDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.append("key", "value")
        assertFalse(decoded.isEmpty())
        assertTrue(rawEncoded.isEmpty())

        encoded.clear()
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun testMethodsDelegateToRawEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.append("key%D0", "value%83")
        assertFalse(rawEncoded.isEmpty())
        assertTrue(decoded.isEmpty())

        encoded.clear()
        assertTrue(rawEncoded.isEmpty())
    }

    @Test
    fun testAppendDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        decoded.append("ke y", "valu e")
        assertEquals("valu+e", encoded["ke%20y"])
        assertEquals("valu e", decoded["ke y"])
    }

    @Test
    fun testAppendEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.append("key%D0", "valu%20e")
        assertEquals("valu%20e", rawEncoded["key%D0"])
        assertEquals("valu%20e", encoded["key%D0"])

        assertFails {
            encoded.append("key", "invalid encoding %%")
        }
    }

    @Test
    fun testAppendAllDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        decoded.appendAll("ke y", listOf("valu e", "v%alue"))
        assertEquals(listOf("valu+e", "v%25alue"), encoded.getAll("ke%20y"))
        assertEquals(listOf("valu e", "v%alue"), decoded.getAll("ke y"))
    }

    @Test
    fun testAppendAllEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.appendAll("ke%D0y", listOf("valu+e", "v%25alue"))
        assertEquals(listOf("valu+e", "v%25alue"), rawEncoded.getAll("ke%D0y"))
        assertEquals(listOf("valu+e", "v%25alue"), encoded.getAll("ke%D0y"))

        encoded.appendAll("ke%20y", listOf("valu%D0e", "value%83"))
        assertEquals(listOf("valu%D0e", "value%83"), rawEncoded.getAll("ke%20y"))
        assertEquals(listOf("valu%D0e", "value%83"), encoded.getAll("ke%20y"))

        assertFails {
            encoded.appendAll("key", listOf("value", "invalid encoding %%"))
        }
    }

    @Test
    fun testContainsDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        decoded.append("ke y", "valu e")
        assertTrue(encoded.contains("ke%20y"))
        assertTrue(decoded.contains("ke y"))
        assertTrue(encoded.contains("ke%20y", "valu+e"))
        assertTrue(decoded.contains("ke y", "valu e"))
        assertFalse(encoded.contains("ke%20y", "valu+e1"))
        assertFalse(decoded.contains("ke y", "valu e1"))
    }

    @Test
    fun testContainsEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.append("ke%D0y", "valu+e")
        assertTrue(encoded.contains("ke%D0y"))
        assertTrue(rawEncoded.contains("ke%D0y"))
        assertTrue(encoded.contains("ke%D0y", "valu+e"))
        assertTrue(rawEncoded.contains("ke%D0y", "valu+e"))
        assertFalse(encoded.contains("ke%D0y", "valu+e1"))
        assertFalse(rawEncoded.contains("ke%D0y", "valu+e1"))
    }

    @Test
    fun testNamesDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        decoded.append("ke y", "valu e")
        decoded.append("ke y1", "valu e1")
        assertEquals(setOf("ke%20y", "ke%20y1"), encoded.names())
        assertEquals(setOf("ke y", "ke y1"), decoded.names())
    }

    @Test
    fun testNamesEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.append("ke%20y", "value%83")
        encoded.append("ke%20y1", "value1%83")
        encoded.append("ke%D0y2", "value+2")
        encoded.append("ke%D0y3", "value3")
        assertEquals(setOf("ke%20y", "ke%20y1", "ke%D0y2", "ke%D0y3"), encoded.names())
        assertEquals(setOf("ke%20y", "ke%20y1", "ke%D0y2", "ke%D0y3"), rawEncoded.names())
    }

    @Test
    fun testRemoveDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        decoded.append("ke y", "valu e")
        decoded.append("ke y", "valu e1")
        decoded.remove("ke y", "valu e1")
        assertTrue(encoded.contains("ke%20y", "valu+e"))
        assertFalse(encoded.contains("ke%20y", "valu+e1"))
        decoded.remove("ke y")
        assertFalse(encoded.contains("ke%20y"))
    }

    @Test
    fun testRemoveEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.append("ke%20y", "valu+e%D0")
        encoded.append("ke%20y", "valu+e1%83")
        encoded.remove("ke%20y", "valu+e1%83")
        assertTrue(encoded.contains("ke%20y", "valu+e%D0"))
        assertFalse(encoded.contains("ke%20y", "valu+e1%83"))
        encoded.remove("ke%20y")
        assertFalse(encoded.contains("ke%20y"))
    }

    @Test
    fun testEntriesDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        decoded.append("ke y", "valu e")
        decoded.append("ke y", "valu e1")
        decoded.append("ke y1", "valu e1")
        val entriesEncoded = encoded.entries()
        val entriesDecoded = decoded.entries()

        assertEquals(2, entriesEncoded.size)
        assertEquals(2, entriesDecoded.size)

        assertEquals(listOf("valu+e", "valu+e1"), entriesEncoded.single { it.key == "ke%20y" }.value)
        assertEquals(listOf("valu+e1"), entriesEncoded.single { it.key == "ke%20y1" }.value)

        assertEquals(listOf("valu e", "valu e1"), entriesDecoded.single { it.key == "ke y" }.value)
        assertEquals(listOf("valu e1"), entriesDecoded.single { it.key == "ke y1" }.value)
    }

    @Test
    fun testEntriesEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.append("ke%20y%D0", "valu+e")
        encoded.append("ke%20y%D0", "valu+e1")
        encoded.append("ke%20y1", "valu+e1%83")
        val entriesEncoded = encoded.entries()
        val entriesRawEncoded = rawEncoded.entries()

        assertEquals(2, entriesEncoded.size)
        assertEquals(2, entriesRawEncoded.size)

        assertEquals(listOf("valu+e", "valu+e1"), entriesEncoded.single { it.key == "ke%20y%D0" }.value)
        assertEquals(listOf("valu+e1%83"), entriesEncoded.single { it.key == "ke%20y1" }.value)

        assertEquals(listOf("valu+e", "valu+e1"), entriesRawEncoded.single { it.key == "ke%20y%D0" }.value)
        assertEquals(listOf("valu+e1%83"), entriesRawEncoded.single { it.key == "ke%20y1" }.value)
    }

    @Test
    fun testAppendStringValueDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        val values = parametersOf("ke y" to listOf("valu e", "valu e1"), "ke y1" to listOf("valu e1"))
        decoded.appendAll(values)

        assertEquals(2, encoded.entries().size)
        assertEquals(2, decoded.entries().size)

        assertEquals(listOf("valu+e", "valu+e1"), encoded.getAll("ke%20y"))
        assertEquals(listOf("valu+e1"), encoded.getAll("ke%20y1"))

        assertEquals(listOf("valu e", "valu e1"), decoded.getAll("ke y"))
        assertEquals(listOf("valu e1"), decoded.getAll("ke y1"))
    }

    @Test
    fun testAppendStringValueEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        val values = parametersOf("ke%20y" to listOf("valu+e%D0", "valu+e1%83"), "ke%20y1%D0" to listOf("valu+e1"))
        encoded.appendAll(values)

        assertEquals(2, encoded.entries().size)
        assertEquals(2, rawEncoded.entries().size)

        assertEquals(listOf("valu+e%D0", "valu+e1%83"), encoded.getAll("ke%20y"))
        assertEquals(listOf("valu+e1"), encoded.getAll("ke%20y1%D0"))

        assertEquals(listOf("valu+e%D0", "valu+e1%83"), rawEncoded.getAll("ke%20y"))
        assertEquals(listOf("valu+e1"), rawEncoded.getAll("ke%20y1%D0"))
    }

    @Test
    fun testBuildDecoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.appendAll("ke%20y", listOf("valu+e", "valu+e1"))
        encoded.appendAll("ke%20y1", listOf("valu+e1"))

        val result = decoded.build()

        assertEquals(listOf("valu e", "valu e1"), result.getAll("ke y"))
        assertEquals(listOf("valu e1"), result.getAll("ke y1"))
    }

    @Test
    fun testBuildEncoded() {
        val rawEncoded = ParametersBuilder()
        val decoded = ParametersBuilder()
        val encoded = UrlEncodedParametersBuilder(rawEncoded, decoded)

        encoded.appendAll("ke%20y%83", listOf("valu+e", "valu+e1"))
        encoded.appendAll("ke%20y1", listOf("valu+e1%D0"))

        val result = encoded.build()

        assertEquals(listOf("valu+e", "valu+e1"), result.getAll("ke%20y%83"))
        assertEquals(listOf("valu+e1%D0"), result.getAll("ke%20y1"))
    }
}
