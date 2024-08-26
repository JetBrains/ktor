/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Escapes the characters in a String using HTML entities
 */
public fun String.escapeHTML(): String {
    val text = this@escapeHTML
    if (text.isEmpty()) return text

    return buildString(length) {
        for (element in text) {
            when (element) {
                '\'' -> append("&#x27;")
                '\"' -> append("&quot;")
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(element)
            }
        }
    }
}

/**
 * Splits the given string into two parts before and after separator.
 *
 * Useful together with destructuring declarations
 */
public inline fun String.chomp(
    separator: String,
    onMissingDelimiter: () -> Pair<String, String>
): Pair<String, String> {
    return when (val idx = indexOf(separator)) {
        -1 -> onMissingDelimiter()
        else -> substring(0, idx) to substring(idx + separator.length)
    }
}

/**
 * Does the same as the regular [toLowerCase] except that locale-specific rules are not applied to ASCII characters
 * so latin characters are converted by the original english rules.
 */
public fun String.toLowerCasePreservingASCIIRules(): String {
    val firstIndex = indexOfFirst {
        toLowerCasePreservingASCII(it) != it
    }

    if (firstIndex == -1) {
        return this
    }

    val original = this
    return buildString(length) {
        append(original, 0, firstIndex)

        for (index in firstIndex..original.lastIndex) {
            append(toLowerCasePreservingASCII(original[index]))
        }
    }
}

/**
 * Does the same as the regular [toUpperCase] except that locale-specific rules are not applied to ASCII characters
 * so latin characters are converted by the original english rules.
 */
public fun String.toUpperCasePreservingASCIIRules(): String {
    val firstIndex = indexOfFirst {
        toUpperCasePreservingASCII(it) != it
    }

    if (firstIndex == -1) {
        return this
    }

    val original = this
    return buildString(length) {
        append(original, 0, firstIndex)

        for (index in firstIndex..original.lastIndex) {
            append(toUpperCasePreservingASCII(original[index]))
        }
    }
}

private fun toLowerCasePreservingASCII(ch: Char): Char = when (ch) {
    in 'A'..'Z' -> ch + 32
    in '\u0000'..'\u007f' -> ch
    else -> ch.lowercaseChar()
}

private fun toUpperCasePreservingASCII(ch: Char): Char = when (ch) {
    in 'a'..'z' -> ch - 32
    in '\u0000'..'\u007f' -> ch
    else -> ch.lowercaseChar()
}

internal fun String.caseInsensitive(): CaseInsensitiveString = CaseInsensitiveString(this)

internal class CaseInsensitiveString(val content: String) {
    private val hash: Int

    init {
        var temp = 0
        for (element in content) {
            temp = temp * 31 + element.lowercaseChar().code
        }

        hash = temp
    }

    override fun equals(other: Any?): Boolean =
        (other as? CaseInsensitiveString)?.content?.equals(content, ignoreCase = true) == true

    override fun hashCode(): Int = hash

    override fun toString(): String = content
}
