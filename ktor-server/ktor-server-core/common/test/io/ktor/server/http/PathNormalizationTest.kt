/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.server.util.*
import kotlin.test.*

class PathNormalizationTest {
    @Test
    fun testEmpty() {
        assertEquals(emptyList(), listOf<String>().normalizePathComponents())
        assertEquals(emptyList(), listOf("").normalizePathComponents())
        assertEquals(emptyList(), listOf("", "").normalizePathComponents())
        assertEquals(emptyList(), listOf(".").normalizePathComponents())
        assertEquals(emptyList(), listOf(".", ".").normalizePathComponents())
        assertEquals(emptyList(), listOf(".", ".", ".").normalizePathComponents())
        assertEquals(emptyList(), listOf(".", "..", ".").normalizePathComponents())
        assertEquals(emptyList(), listOf("..").normalizePathComponents())
        assertEquals(emptyList(), listOf("..", "..").normalizePathComponents())
        assertEquals(emptyList(), listOf("..", "..", "..").normalizePathComponents())
    }

    @Test
    fun testDirUp() {
        assertEquals(listOf("a"), listOf("a").normalizePathComponents())
        assertEquals(listOf("a"), listOf("..", "a").normalizePathComponents())
        assertEquals(listOf("a"), listOf("..", "..", "a").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "..").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "..", "..").normalizePathComponents())
        assertEquals(listOf("b"), listOf("a", "..", "..", "b").normalizePathComponents())

        assertEquals(listOf("a", "b"), listOf("a", "b").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a", "b", "..").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "b", "..", "..").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "b", "..", "..", "..").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "..", "b", "..").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "..", "..", "b", "..").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "..", "b", "..", "..").normalizePathComponents())
        assertEquals(listOf(), listOf("a", "..", "..", "b", "..", "..").normalizePathComponents())
        assertEquals(listOf(), generateSequence { ".." }.take(1000).toList().normalizePathComponents())
    }

    @Test
    fun testNoOp() {
        assertEquals(listOf(), listOf(".").normalizePathComponents())
        assertEquals(listOf(), listOf(".", ".").normalizePathComponents())
        assertEquals(listOf(), listOf(".", ".", ".").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a", ".").normalizePathComponents())
        assertEquals(listOf("a"), listOf(".", "a").normalizePathComponents())
        assertEquals(listOf("a"), listOf(".", "a", ".").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf("a", "b").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf("a", ".", "b").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf("a", ".", "b", ".").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf(".", "a", ".", "b", ".").normalizePathComponents())
    }

    @Test
    fun testComponentEndsWith() {
        assertEquals(listOf("a"), listOf("a.").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a..").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a...").normalizePathComponents())

        assertEquals(listOf("a"), listOf("a ").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a  ").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a   ").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a .").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a. ").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a. . ").normalizePathComponents())
        assertEquals(listOf("a"), listOf("a . .").normalizePathComponents())

        assertEquals(listOf("-", "a"), listOf("-", "a.").normalizePathComponents())
        assertEquals(listOf("-", "a"), listOf("-", "a ").normalizePathComponents())
        assertEquals(listOf("-", "a", "b"), listOf("-", "a.", "b.").normalizePathComponents())
    }

    @Test
    fun testComponentWithNull() {
        assertEquals(listOf("a"), listOf("a\u0000").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf("a", "b\u0000").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf("a\u0000", "b\u0000").normalizePathComponents())
    }

    @Test
    fun testComponentWithTilde() {
        assertEquals(listOf(), listOf("~").normalizePathComponents())
        assertEquals(listOf(), listOf("~", "~").normalizePathComponents())
        assertEquals(listOf("~test"), listOf("~test").normalizePathComponents())
        assertEquals(listOf("~~"), listOf("~~").normalizePathComponents())
    }

    @Test
    fun testControlCharactersAndProhibited() {
        val controlCharacters = '\u0000'..'\u001f'
        val prohibitedCharacters = listOf('\\', '/', ':', '*', '?', '\"', '<', '>', '|')

        for (ch in controlCharacters + prohibitedCharacters) {
            assertEquals(listOf(), listOf("$ch").normalizePathComponents())
            assertEquals(listOf(), listOf("$ch$ch").normalizePathComponents())
            assertEquals(listOf(), listOf("$ch$ch$ch").normalizePathComponents())
            assertEquals(listOf(), listOf("$ch", "$ch").normalizePathComponents())
            assertEquals(listOf("a"), listOf("${ch}a").normalizePathComponents())
            assertEquals(listOf("a"), listOf("a$ch").normalizePathComponents())
            assertEquals(listOf("ab"), listOf("a${ch}b").normalizePathComponents())
            assertEquals(listOf("ab"), listOf("a${ch}b", "$ch").normalizePathComponents())
            assertEquals(listOf("ab", "c"), listOf("a${ch}b", "$ch", "c").normalizePathComponents())
        }
    }

    @Test
    fun testWindowsDeviceNames() {
        val names = listOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )
        val allNames = names +
            names.map { it.lowercase() } +
            names.map { name ->
                name.lowercase()
                    .replaceFirstChar { char ->
                        if (char.isLowerCase()) {
                            char.titlecase()
                        } else {
                            char.toString()
                        }
                    }
            }

        for (name in allNames) {
            assertEquals(listOf(), listOf(name).normalizePathComponents())
            assertEquals(listOf(), listOf(name, name).normalizePathComponents())
            assertEquals(listOf("a"), listOf(name, "a", name).normalizePathComponents())
            assertEquals(listOf("a"), listOf(name, "a").normalizePathComponents())
            assertEquals(listOf("a"), listOf("a", name).normalizePathComponents())
            assertEquals(listOf("a", "b"), listOf("a", name, "b").normalizePathComponents())
        }
    }

    @Test
    fun testTrailingSpaces() {
        assertEquals(listOf("a", "b"), listOf("a", ". ", "b").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf("a", ".. ", "b").normalizePathComponents())
        assertEquals(listOf("a", "b"), listOf("a", " ..", "b").normalizePathComponents())
    }
}
