@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.core

import io.ktor.utils.io.*
import kotlinx.io.Buffer

/**
 * Represents a buffer with read and write positions.
 *
 * Concurrent unsafe: the same memory could be shared between different instances of [Buffer] however you can't
 * read/write using the same [Buffer] instance from different threads.
 */
@Deprecated(
    IO_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith("Buffer", "kotlinx.io.Buffer")
)
public typealias Buffer = kotlinx.io.Buffer

public fun Buffer.canRead(): Boolean = !exhausted()
