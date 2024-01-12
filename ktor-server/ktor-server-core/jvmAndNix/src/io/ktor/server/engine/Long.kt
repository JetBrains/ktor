/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

private val longStrings = Array(1024) {
    it.toString()
}

internal fun Long.toStringFast() = if (this in 0..1023) longStrings[toInt()] else toString()
