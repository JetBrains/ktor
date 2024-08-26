/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*
import io.ktor.utils.io.charsets.*

/**
 * Default [ContentType] for [extension]
 */
public fun ContentType.Companion.defaultForFileExtension(extension: String): ContentType =
    ContentType.fromFileExtension(extension).selectDefault()

/**
 * Default [ContentType] for file [path]
 */
public fun ContentType.Companion.defaultForFilePath(path: String): ContentType =
    ContentType.fromFilePath(path).selectDefault()

/**
 * Recommended content types by file [path]
 */
public fun ContentType.Companion.fromFilePath(path: String): List<ContentType> {
    val slashIndex = path.lastIndexOfAny("/\\".toCharArray())
    val index = path.indexOf('.', startIndex = slashIndex + 1)
    if (index == -1) {
        return emptyList()
    }
    return fromFileExtension(path.substring(index + 1))
}

/**
 * Recommended content type by file name extension
 */
public fun ContentType.Companion.fromFileExtension(ext: String): List<ContentType> {
    var current = ext.removePrefix(".").toLowerCasePreservingASCIIRules()
    while (current.isNotEmpty()) {
        val type = contentTypesByExtensions[current]
        if (type != null) {
            return type
        }
        current = current.substringAfter(".", "")
    }

    return emptyList()
}

/**
 * Recommended file name extensions for this content type
 */
public fun ContentType.fileExtensions(): List<String> = extensionsByContentType[this]
    ?: extensionsByContentType[this.withoutParameters()]
    ?: emptyList()

private val contentTypesByExtensions: Map<String, List<ContentType>> by lazy {
    caseInsensitiveMap<List<ContentType>>().apply { putAll(mimes.asSequence().groupByPairs()) }
}

private val extensionsByContentType: Map<ContentType, List<String>> by lazy {
    mimes.asSequence().map { (first, second) -> second to first }.groupByPairs()
}

internal fun List<ContentType>.selectDefault(): ContentType {
    val contentType = firstOrNull() ?: ContentType.Application.OctetStream
    return when {
        contentType.match(ContentType.Text.Any) -> contentType.withCharsetUTF8IfNeeded()
        contentType.match(ContentType.Image.SVG) -> contentType.withCharsetUTF8IfNeeded()
        contentType.matchApplicationTypeWithCharset() -> contentType.withCharsetUTF8IfNeeded()
        else -> contentType
    }
}

private fun ContentType.matchApplicationTypeWithCharset(): Boolean {
    if (!match(ContentType.Application.Any)) return false

    return when {
        match(ContentType.Application.Atom) ||
            match(ContentType.Application.JavaScript) ||
            match(ContentType.Application.Rss) ||
            match(ContentType.Application.Xml) ||
            match(ContentType.Application.Xml_Dtd)
        -> true

        else -> false
    }
}

private fun ContentType.withCharsetUTF8IfNeeded(): ContentType {
    if (charset() != null) return this

    return withCharset(Charsets.UTF_8)
}

internal fun <A, B> Sequence<Pair<A, B>>.groupByPairs() = groupBy { it.first }
    .mapValues { e -> e.value.map { it.second } }

internal fun String.toContentType() = try {
    ContentType.parse(this)
} catch (e: Throwable) {
    throw IllegalArgumentException("Failed to parse $this", e)
}
