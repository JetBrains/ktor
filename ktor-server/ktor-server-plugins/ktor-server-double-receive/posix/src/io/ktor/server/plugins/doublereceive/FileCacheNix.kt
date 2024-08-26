// ktlint-disable filename
/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.utils.io.*
import kotlin.coroutines.*

internal actual class FileCache actual constructor(
    body: ByteReadChannel,
    bufferSize: Int,
    context: CoroutineContext
) : DoubleReceiveCache {
    init {
        error("File cache is not supported on nix")
    }

    actual override fun read(): ByteReadChannel {
        error("File cache is not supported on nix")
    }

    actual override fun dispose() {
        error("File cache is not supported on nix")
    }
}
