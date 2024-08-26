/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
public suspend fun ByteWriteChannel.writeFully(value: CPointer<ByteVar>, offset: Int, length: Int) {
    writeFully(value, offset.toLong(), length.toLong())
}

@OptIn(ExperimentalForeignApi::class, InternalAPI::class)
public suspend fun ByteWriteChannel.writeFully(src: CPointer<ByteVar>, offset: Long, length: Long) {
    writeBuffer.writeFully(src, offset, length)
    flush()
}
