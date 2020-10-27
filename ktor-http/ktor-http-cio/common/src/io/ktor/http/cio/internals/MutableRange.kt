/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

import io.ktor.util.*

/**
 * Represents a text range with mutable bounds
 * @param start points to the first character
 * @param end points to the next character after the last one
 */
@InternalAPI
public class MutableRange(public var start: Int, public var end: Int) {
    override fun toString(): String = "MutableRange(start=$start, end=$end)"
}
