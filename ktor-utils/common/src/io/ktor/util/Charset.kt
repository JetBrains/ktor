/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Check if [Char] is in lower case
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.isLowerCase)
 */
public fun Char.isLowerCase(): Boolean = lowercaseChar() == this

/**
 * Convert [String] to [CharArray]
 */
public fun String.toCharArray(): CharArray = CharArray(length) { get(it) }
