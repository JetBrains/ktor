/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import java.io.*

/**
 * Append a [relativePath] safely that means that adding any extra `..` path elements will not let
 * access anything out of the reference directory (unless you have symbolic or hard links or multiple mount points)
 */
public fun File.combineSafe(relativePath: String): File = combineSafe(this, File(relativePath))

/**
 * Remove all redundant `.` and `..` path elements. Leading `..` are also considered redundant.
 */
public fun File.normalizeAndRelativize(): File = normalize().notRooted().dropLeadingTopDirs()

private fun combineSafe(dir: File, relativePath: File): File {
    val normalized = relativePath.normalizeAndRelativize()
    if (normalized.startsWith("..")) {
        throw IllegalArgumentException("Bad relative path $relativePath")
    }
    check(!normalized.isAbsolute) { "Bad relative path $relativePath" }

    return File(dir, normalized.path)
}

private fun File.notRooted(): File {
    if (!isRooted) return this

    var current: File = this

    while (true) {
        val parent = current.parentFile ?: break
        current = parent
    }

    // current = this.root

    return File(path.drop(current.name.length).dropWhile { it == '\\' || it == '/' })
}

/**
 * Discards all leading path separators, top dir (..) and current dir (.) references.
 * @return the remaining part of the original [path], possibly empty.
 */
internal fun dropLeadingTopDirs(path: String): Int {
    var startIndex = 0
    val lastIndex = path.length - 1

    while (startIndex <= lastIndex) {
        val first = path[startIndex]
        if (first.isPathSeparator()) {
            startIndex++
            continue
        }
        if (first != '.') {
            break
        }

        if (startIndex == lastIndex) {
            startIndex++
            break
        }

        val second: Char = path[startIndex + 1]
        startIndex += if (second.isPathSeparator()) {
            2 // skip 2 characters: ./ or .\
        } else if (second == '.') {
            if (startIndex + 2 == path.length) {
                2 // skip the only 2 characters remaining: ..
            } else if (path[startIndex + 2].isPathSeparator()) {
                3 // skip 3 characters: ../ or ..\
            } else { // we have a path component starting with two dots that shouldn't be discarded
                break
            }
        } else { // we have a path component starting with a single dot
            break
        }
    }

    return startIndex
}

private fun Char.isPathSeparator(): Boolean = this == '\\' || this == '/'
private fun Char.isPathSeparatorOrDot(): Boolean = this == '.' || isPathSeparator()

private fun File.dropLeadingTopDirs(): File {
    val startIndex = dropLeadingTopDirs(path ?: "")

    if (startIndex == 0) return this
    if (startIndex >= path.length) return File(".")

    return File(path.substring(startIndex))
}
