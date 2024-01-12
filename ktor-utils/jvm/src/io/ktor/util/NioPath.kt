/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import java.io.*
import java.nio.file.*

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 */
public fun Path.combineSafe(relativePath: Path): Path {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Relative path $relativePath beginning with .. is invalid")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath" }

    return resolve(normalized)
}

/**
 * Remove all redundant `.` and `..` path elements. Leading `..` are also considered redundant.
 */
public fun Path.normalizeAndRelativize(): Path =
    root?.relativize(this)?.normalize()?.dropLeadingTopDirs() ?: normalize().dropLeadingTopDirs()

private fun Path.dropLeadingTopDirs(): Path {
    val startIndex = indexOfFirst { it.toString() != ".." }
    if (startIndex <= 0) return this
    return subpath(startIndex, nameCount)
}

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 */
public fun File.combineSafe(relativePath: Path): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw InvalidPathException(relativePath.toString(), "Relative path $relativePath beginning with .. is invalid")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath" }

    return File(this, normalized.toString())
}
