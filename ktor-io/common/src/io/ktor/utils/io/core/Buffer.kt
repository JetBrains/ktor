/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.core

import io.ktor.utils.io.*
import kotlinx.io.Buffer

/**
 * Represents a buffer with read and write positions.
 *
 * Concurrent unsafe: the same memory could be shared between different instances of [Buffer] however you can't
 * read/write using the same [Buffer] instance from different threads.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.core.Buffer)
 */
@Deprecated(
    IO_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith("Buffer", "kotlinx.io.Buffer")
)
public typealias Buffer = kotlinx.io.Buffer

public fun Buffer.canRead(): Boolean = !exhausted()
