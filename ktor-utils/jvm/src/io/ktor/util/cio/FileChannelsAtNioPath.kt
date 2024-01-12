/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.cio

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.nio.file.*
import kotlin.coroutines.*
import kotlin.io.use

/**
 * Launches a coroutine to open a read channel for a file and fill it.
 * Please note that file reading is blocking so if you are starting it on [Dispatchers.Unconfined] it may block
 * your async code and freeze the whole application when runs on a pool that is not intended for blocking operations.
 * This is why [coroutineContext] should have [Dispatchers.IO] or
 * a coroutine dispatcher that is properly configured for blocking IO.
 */
public fun Path.readChannel(
    start: Long = 0,
    endInclusive: Long = -1,
    coroutineContext: CoroutineContext = Dispatchers.IO
): ByteReadChannel {
    val fileLength = Files.size(this)
    return CoroutineScope(coroutineContext).writer(CoroutineName("file-reader") + coroutineContext, autoFlush = false) {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= fileLength - 1) {
            "endInclusive points to the position out of the file: file size = $fileLength, endInclusive = $endInclusive"
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        Files.newByteChannel(this@readChannel).use { fileChannel ->
            fileChannel.writeToScope(this, start, endInclusive)
        }
    }.channel
}
