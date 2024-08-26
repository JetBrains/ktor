/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.parsing

internal interface Parser {
    fun parse(input: String): ParseResult?

    fun match(input: String): Boolean
}

internal class ParseResult(
    private val mapping: Map<String, List<String>>
) {
    operator fun get(key: String): String? = mapping[key]?.firstOrNull()
    fun getAll(key: String): List<String> = mapping[key] ?: emptyList()

    fun contains(key: String): Boolean = mapping.contains(key)
}
