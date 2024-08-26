/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/** Separator symbols listed in RFC https://tools.ietf.org/html/rfc2616#section-2.2 */
private val HeaderFieldValueSeparators =
    setOf('(', ')', '<', '>', '@', ',', ';', ':', '\\', '\"', '/', '[', ']', '?', '=', '{', '}', ' ', '\t', '\n', '\r')

/**
 * Represents a header value that consist of [content] followed by [parameters].
 * Useful for headers such as `Content-Type`, `Content-Disposition` and so on.
 *
 * @property content header's content without parameters
 * @property parameters
 */
public abstract class HeaderValueWithParameters(
    protected val content: String,
    public val parameters: List<HeaderValueParam> = emptyList()
) {

    /**
     * The first value for the parameter with [name] comparing case-insensitively or `null` if no such parameters found
     */
    public fun parameter(name: String): String? {
        for (index in 0..parameters.lastIndex) {
            val parameter = parameters[index]

            if (parameter.name.equals(name, ignoreCase = true)) {
                return parameter.value
            }
        }

        return null
    }

    override fun toString(): String = when {
        parameters.isEmpty() -> content
        else -> {
            val size = content.length + parameters.sumOf { it.name.length + it.value.length + 3 }

            StringBuilder(size).apply {
                append(content)
                for (index in 0..parameters.lastIndex) {
                    val element = parameters[index]
                    append("; ")
                    append(element.name)
                    append("=")
                    element.value.escapeIfNeededTo(this)
                }
            }.toString()
        }
    }

    public companion object {
        /**
         * Parse header with parameter and pass it to [init] function to instantiate particular type
         */
        public inline fun <R> parse(value: String, init: (String, List<HeaderValueParam>) -> R): R {
            val headerValue = parseHeaderValue(value).last()
            return init(headerValue.value, headerValue.params)
        }
    }
}

/**
 * Append formatted header value to the builder
 */
public fun StringValuesBuilder.append(name: String, value: HeaderValueWithParameters) {
    append(name, value.toString())
}

/**
 * Escape using double quotes if needed or keep as is if no dangerous strings found
 */
public fun String.escapeIfNeeded(): String = when {
    needQuotes() -> quote()
    else -> this
}

@Suppress("NOTHING_TO_INLINE")
private inline fun String.escapeIfNeededTo(out: StringBuilder) {
    when {
        needQuotes() -> out.append(quote())
        else -> out.append(this)
    }
}

private fun String.needQuotes(): Boolean {
    if (isEmpty()) return true
    if (isQuoted()) return false

    for (element in this) {
        if (HeaderFieldValueSeparators.contains(element)) return true
    }

    return false
}

private fun String.isQuoted(): Boolean {
    if (length < 2) {
        return false
    }
    if (first() != '"' || last() != '"') {
        return false
    }
    var startIndex = 1
    do {
        val index = indexOf('"', startIndex)
        if (index == lastIndex) {
            break
        }

        var slashesCount = 0
        var slashIndex = index - 1
        while (this[slashIndex] == '\\') {
            slashesCount++
            slashIndex--
        }
        if (slashesCount % 2 == 0) {
            return false
        }

        startIndex = index + 1
    } while (startIndex < length)

    return true
}

/**
 * Escape string using double quotes
 */
public fun String.quote(): String = buildString { this@quote.quoteTo(this) }

private fun String.quoteTo(out: StringBuilder) {
    out.append("\"")
    for (element in this) {
        when (val ch = element) {
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\"' -> out.append("\\\"")
            else -> out.append(ch)
        }
    }
    out.append("\"")
}
