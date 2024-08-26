/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.cinterop.*

/**
 * Reads all available bytes to [dst] buffer and returns immediately or suspends if no bytes available
 * @return number of bytes were read or `-1` if the channel has been closed
 */
@OptIn(ExperimentalForeignApi::class, InternalAPI::class)
public suspend fun ByteReadChannel.readAvailable(dst: CPointer<ByteVar>, offset: Int, length: Int): Int {
    if (availableForRead == 0) awaitContent()
    if (isClosedForRead) return -1

    return readBuffer.readAvailable(dst, offset, length)
}
