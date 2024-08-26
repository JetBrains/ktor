/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.util

import io.ktor.util.*

/**
 * Process path components such as `.` and `..`, replacing redundant path components including all leading.
 * It also discards all reserved characters and component names that are reserved (such as `CON`, `NUL`).
 */
public fun List<String>.normalizePathComponents(): List<String> {
    for (index in indices) {
        val component = get(index)
        if (component.shouldBeReplaced()) {
            return filterComponentsImpl(index)
        }
    }

    return this
}

private fun List<String>.filterComponentsImpl(startIndex: Int): List<String> {
    val result = ArrayList<String>(size)
    if (startIndex > 0) {
        result.addAll(subList(0, startIndex))
    }
    result.processAndReplaceComponent(get(startIndex))
    for (index in startIndex + 1 until size) {
        val component = get(index)
        if (component.shouldBeReplaced()) {
            result.processAndReplaceComponent(component)
        } else {
            result.add(component)
        }
    }

    return result
}

private fun MutableList<String>.processAndReplaceComponent(component: String) {
    if (component.isEmpty() ||
        component == "." || component == "~" || component.toUpperCasePreservingASCIIRules() in ReservedWords
    ) {
        return
    }
    if (component == "..") {
        if (isNotEmpty()) {
            removeAt(lastIndex)
        }
        return
    }

    component.filter { it >= ' ' && it !in ReservedCharacters }
        .trimEnd { it == ' ' || it == '.' }
        .takeIf { it.isNotEmpty() }?.let { filtered ->
            add(filtered)
        }
}

private val FirstReservedLetters = charArrayOf('A', 'a', 'C', 'c', 'l', 'L', 'P', 'p', 'n', 'N').toASCIITable()

private val ReservedWords = setOf(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
)

private val ReservedCharacters = charArrayOf('\\', '/', ':', '*', '?', '\"', '<', '>', '|').toASCIITable()

@Suppress("LocalVariableName")
private fun String.shouldBeReplaced(): Boolean {
    val length = length
    if (length == 0) return true
    val first = this[0]

    if (first == '.' && (length == 1 || (length == 2 && this[1] == '.'))) {
        // replace . and ..
        return true
    }
    if (first == '~' && length == 1) {
        return true
    }

    if (first in FirstReservedLetters &&
        (this in ReservedWords || this.toUpperCasePreservingASCIIRules() in ReservedWords)
    ) {
        return true
    }

    val last = this[length - 1]
    if (last == ' ' || last == '.') {
        // not allowed in Windows
        return true
    }

    val ReservedCharacters = ReservedCharacters
    // control characters are not allowed on windows, \0 is not allowed on UNIX
    return any { it < ' ' || it in ReservedCharacters }
}

private fun CharArray.toASCIITable(): BooleanArray = BooleanArray(0x100) { it.toChar() in this@toASCIITable }
private operator fun BooleanArray.contains(char: Char): Boolean {
    val codepoint = char.code
    return codepoint < size && this[codepoint]
}
