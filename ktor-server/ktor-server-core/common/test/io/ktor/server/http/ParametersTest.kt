/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.server.util.*
import kotlin.test.*

class ParametersTest {
    private val parameters = parametersOf(
        "single" to listOf("a"),
        "another" to listOf("2"),
        "multiple" to listOf("3", "4")
    )

    @Test
    fun testSingleValues() {
        val single: String by parameters
        val another: Int by parameters

        assertEquals("a", single)
        assertEquals(2, another)
    }

    @Test
    fun testMultipleAsStrings() {
        val multiple: List<String> by parameters

        assertEquals(listOf("3", "4"), multiple)
    }

    @Test
    fun testMultipleAsStringsVariance() {
        val multiple: MutableList<out String> by parameters
        assertEquals(listOf<Any?>("3", "4"), multiple.toList())
    }

    @Test
    fun testMultipleAsIntegers() {
        val multiple: List<Int> by parameters

        assertEquals(listOf(3, 4), multiple)
    }

    @Test
    fun testMultipleAsLongIntegers() {
        val multiple: List<Long> by parameters

        assertEquals(listOf(3L, 4L), multiple)
    }

    @Test
    fun parametersBuilderTest() {
        val params = parameters {
            append("x", "1")
            appendAll("y", listOf("2", "3"))
        }
        assertEquals(listOf("1"), params.getAll("x"))
        assertEquals(listOf("2", "3"), params.getAll("y"))
    }
}
