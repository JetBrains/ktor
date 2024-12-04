/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Represents a single value parameter
 * @property name of parameter
 * @property value of parameter
 * @property escapeValue specifies if the value should be escaped
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HeaderValueParam)
 */
public data class HeaderValueParam(val name: String, val value: String, val escapeValue: Boolean) {

    public constructor(name: String, value: String) : this(name, value, false)

    override fun equals(other: Any?): Boolean {
        return other is HeaderValueParam &&
            other.name.equals(name, ignoreCase = true) &&
            other.value.equals(value, ignoreCase = true)
    }
    override fun hashCode(): Int {
        var result = name.lowercase().hashCode()
        result += 31 * result + value.lowercase().hashCode()
        return result
    }
}

/**
 * Represents a header value. Similar to [HeaderValueWithParameters]
 * @property value
 * @property params for this value (could be empty)
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HeaderValue)
 */
public data class HeaderValue(val value: String, val params: List<HeaderValueParam> = emptyList()) {
    /**
     * Value's quality according to `q` parameter or `1.0` if missing or invalid
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HeaderValue.quality)
     */
    val quality: Double = params.firstOrNull { it.name == "q" }
        ?.value
        ?.toDoubleOrNull()
        ?.takeIf { it in 0.0..1.0 }
        ?: 1.0
}

/**
 * Parse header value and sort multiple values according to qualities
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.parseAndSortHeader)
 */
public fun parseAndSortHeader(header: String?): List<HeaderValue> =
    parseHeaderValue(header).sortedByDescending { it.quality }

/**
 * Parse `Content-Type` header values and sort them by quality and asterisks quantity
 */
public fun parseAndSortContentTypeHeader(header: String?): List<HeaderValue> = parseHeaderValue(header).sortedWith(
    compareByDescending<HeaderValue> { it.quality }.thenBy {
        val contentType = ContentType.parse(it.value)
        var asterisks = 0
        if (contentType.contentType == "*") {
            asterisks += 2
        }
        if (contentType.contentSubtype == "*") {
            asterisks++
        }
        asterisks
    }.thenByDescending { it.params.size }
)

/**
 * Parse header value respecting multi-values
 */
public fun parseHeaderValue(text: String?): List<HeaderValue> {
    return parseHeaderValue(text, false)
}

/**
 * Parse header value respecting multi-values
 * @param parametersOnly if no header value itself, only parameters
 */
public fun parseHeaderValue(text: String?, parametersOnly: Boolean): List<HeaderValue> {
    if (text == null) {
        return emptyList()
    }

    var position = 0
    val items = lazy(LazyThreadSafetyMode.NONE) { arrayListOf<HeaderValue>() }
    while (position <= text.lastIndex) {
        position = parseHeaderValueItem(text, position, items, parametersOnly)
    }
    return items.valueOrEmpty()
}

/**
 * Construct a list of [HeaderValueParam] from an iterable of pairs
 */
public fun Iterable<Pair<String, String>>.toHeaderParamsList(): List<HeaderValueParam> =
    map { HeaderValueParam(it.first, it.second) }

private fun <T> Lazy<List<T>>.valueOrEmpty(): List<T> = if (isInitialized()) value else emptyList()
private fun String.subtrim(start: Int, end: Int): String {
    return substring(start, end).trim()
}

private fun parseHeaderValueItem(
    text: String,
    start: Int,
    items: Lazy<ArrayList<HeaderValue>>,
    parametersOnly: Boolean
): Int {
    var position = start
    val parameters = lazy(LazyThreadSafetyMode.NONE) { arrayListOf<HeaderValueParam>() }
    var valueEnd: Int? = if (parametersOnly) position else null

    while (position <= text.lastIndex) {
        when (text[position]) {
            ',' -> {
                items.value.add(HeaderValue(text.subtrim(start, valueEnd ?: position), parameters.valueOrEmpty()))
                return position + 1
            }

            ';' -> {
                if (valueEnd == null) valueEnd = position
                position = parseHeaderValueParameter(text, position + 1, parameters)
            }

            else -> {
                position = if (parametersOnly) {
                    parseHeaderValueParameter(text, position, parameters)
                } else {
                    position + 1
                }
            }
        }
    }

    items.value.add(HeaderValue(text.subtrim(start, valueEnd ?: position), parameters.valueOrEmpty()))
    return position
}

private fun parseHeaderValueParameter(text: String, start: Int, parameters: Lazy<ArrayList<HeaderValueParam>>): Int {
    fun addParam(text: String, start: Int, end: Int, value: String) {
        val name = text.subtrim(start, end)
        if (name.isEmpty()) {
            return
        }

        parameters.value.add(HeaderValueParam(name, value))
    }

    var position = start
    while (position <= text.lastIndex) {
        when (text[position]) {
            '=' -> {
                val (paramEnd, paramValue) = parseHeaderValueParameterValue(text, position + 1)
                addParam(text, start, position, paramValue)
                return paramEnd
            }

            ';', ',' -> {
                addParam(text, start, position, "")
                return position
            }

            else -> position++
        }
    }

    addParam(text, start, position, "")
    return position
}

private fun parseHeaderValueParameterValue(value: String, start: Int): Pair<Int, String> {
    if (value.length == start) {
        return start to ""
    }

    var position = start
    if (value[start] == '"') {
        return parseHeaderValueParameterValueQuoted(value, position + 1)
    }

    while (position <= value.lastIndex) {
        when (value[position]) {
            ';', ',' -> return position to value.subtrim(start, position)
            else -> position++
        }
    }
    return position to value.subtrim(start, position)
}

private fun parseHeaderValueParameterValueQuoted(value: String, start: Int): Pair<Int, String> {
    var position = start
    val builder = StringBuilder()
    loop@ while (position <= value.lastIndex) {
        val currentChar = value[position]

        when {
            currentChar == '"' && value.nextIsSemicolonOrEnd(position) -> {
                return position + 1 to builder.toString()
            }

            currentChar == '\\' && position < value.lastIndex - 2 -> {
                builder.append(value[position + 1])
                position += 2
                continue@loop
            }
        }

        builder.append(currentChar)
        position++
    }

    // The value is unquoted here
    return position to '"' + builder.toString()
}

private fun String.nextIsSemicolonOrEnd(start: Int): Boolean {
    var position = start + 1
    loop@ while (position < length && get(position) == ' ') {
        position += 1
    }

    return position == length || get(position) == ';'
}
