/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Parse query string withing starting at the specified [startIndex] but up to [limit] pairs
 */
public fun parseQueryString(query: String, startIndex: Int = 0, limit: Int = 1000, decode: Boolean = true): Parameters {
    return if (startIndex > query.lastIndex) {
        Parameters.Empty
    } else {
        Parameters.build { parse(query, startIndex, limit, decode) }
    }
}

private fun ParametersBuilder.parse(query: String, startIndex: Int, limit: Int, decode: Boolean) {
    var count = 0
    var nameIndex = startIndex
    var equalIndex = -1
    for (index in startIndex..query.lastIndex) {
        if (count == limit) {
            return
        }
        when (query[index]) {
            '&' -> {
                appendParam(query, nameIndex, equalIndex, index, decode)
                nameIndex = index + 1
                equalIndex = -1
                count++
            }
            '=' -> {
                if (equalIndex == -1) {
                    equalIndex = index
                }
            }
        }
    }
    if (count == limit) {
        return
    }
    appendParam(query, nameIndex, equalIndex, query.length, decode)
}

private fun ParametersBuilder.appendParam(
    query: String,
    nameIndex: Int,
    equalIndex: Int,
    endIndex: Int,
    decode: Boolean
) {
    if (equalIndex == -1) {
        val spaceNameIndex = trimStart(nameIndex, endIndex, query)
        val spaceEndIndex = trimEnd(spaceNameIndex, endIndex, query)

        if (spaceEndIndex > spaceNameIndex) {
            val name = when {
                decode -> query.decodeURLQueryComponent(spaceNameIndex, spaceEndIndex)
                else -> query.substring(spaceNameIndex, spaceEndIndex)
            }
            appendAll(name, emptyList())
        }
        return
    }
    val spaceNameIndex = trimStart(nameIndex, equalIndex, query)
    val spaceEqualIndex = trimEnd(spaceNameIndex, equalIndex, query)
    if (spaceEqualIndex > spaceNameIndex) {
        val name = when {
            decode -> query.decodeURLQueryComponent(spaceNameIndex, spaceEqualIndex)
            else -> query.substring(spaceNameIndex, spaceEqualIndex)
        }

        val spaceValueIndex = trimStart(equalIndex + 1, endIndex, query)
        val spaceEndIndex = trimEnd(spaceValueIndex, endIndex, query)
        val value = when {
            decode -> query.decodeURLQueryComponent(spaceValueIndex, spaceEndIndex, plusIsSpace = true)
            else -> query.substring(spaceValueIndex, spaceEndIndex)
        }
        append(name, value)
    }
}

private fun trimEnd(start: Int, end: Int, text: CharSequence): Int {
    var spaceIndex = end
    while (spaceIndex > start && text[spaceIndex - 1].isWhitespace()) spaceIndex--
    return spaceIndex
}

private fun trimStart(start: Int, end: Int, query: CharSequence): Int {
    var spaceIndex = start
    while (spaceIndex < end && query[spaceIndex].isWhitespace()) spaceIndex++
    return spaceIndex
}
