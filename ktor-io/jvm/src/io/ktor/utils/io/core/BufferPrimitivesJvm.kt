/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlinx.io.*
import kotlinx.io.Buffer
import java.nio.*

/**
 * Write [source] buffer content moving its position.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.core.writeByteBuffer)
 */
@Deprecated(
    "[writeByteBuffer] is deprecated. Consider using [transferFrom] instead",
    replaceWith = ReplaceWith("this.transferFrom(source)")
)
public fun Buffer.writeByteBuffer(source: ByteBuffer) {
    transferFrom(source)
}
