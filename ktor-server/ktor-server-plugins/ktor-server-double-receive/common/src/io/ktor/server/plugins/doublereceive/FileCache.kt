/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.utils.io.*
import kotlin.coroutines.*

internal expect class FileCache(
    body: ByteReadChannel,
    bufferSize: Int = 4096,
    context: CoroutineContext = EmptyCoroutineContext
) : DoubleReceiveCache {
    override fun read(): ByteReadChannel
    override fun dispose()
}
