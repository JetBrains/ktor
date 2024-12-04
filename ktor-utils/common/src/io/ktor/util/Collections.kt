/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Create an instance of case-insensitive mutable map. For internal use only.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.caseInsensitiveMap)
 */
public fun <Value : Any> caseInsensitiveMap(): MutableMap<String, Value> = CaseInsensitiveMap()

/**
 * Freeze selected set. May do nothing on some platforms.
 */
public expect fun <T> Set<T>.unmodifiable(): Set<T>
