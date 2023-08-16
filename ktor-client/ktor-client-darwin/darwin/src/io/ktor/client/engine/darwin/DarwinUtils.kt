/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.*

@OptIn(DelicateCoroutinesApi::class, UnsafeNumber::class, InternalAPI::class)
internal suspend fun OutgoingContent.toDataOrStream(): Any? {
    if (this is OutgoingContent.ByteArrayContent) return bytes().toNSData()
    if (this is OutgoingContent.NoContent) return null
    if (this is OutgoingContent.ProtocolUpgrade) throw UnsupportedContentTypeException(this)

    val outputStreamPtr = nativeHeap.alloc<ObjCObjectVar<NSOutputStream?>>()
    val inputStreamPtr = nativeHeap.alloc<ObjCObjectVar<NSInputStream?>>()

    NSStream.getBoundStreamsWithBufferSize(4096, inputStreamPtr.ptr, outputStreamPtr.ptr)

    val context = callContext()
    context[Job]!!.invokeOnCompletion {
        nativeHeap.free(inputStreamPtr)
        nativeHeap.free(outputStreamPtr)
    }

    val inputStream = inputStreamPtr.value ?: throw IllegalStateException("Failed to create input stream")
    val outputStream = outputStreamPtr.value ?: throw IllegalStateException("Failed to create output stream")

    val channel = when (this) {
        is OutgoingContent.WriteChannelContent -> GlobalScope.writer(context) { writeTo(channel) }.channel
        is OutgoingContent.ReadChannelContent -> readFrom()
        else -> throw UnsupportedContentTypeException(this)
    }

    CoroutineScope(context).launch {
        try {
            outputStream.open()
            memScoped {
                val buffer = allocArray<ByteVar>(4096)
                while (!channel.isClosedForRead) {
                    var offset = 0
                    val read = channel.readAvailable(buffer, 0, 4096)
                    while (offset < read) {
                        while (!outputStream.hasSpaceAvailable) {
                            yield()
                        }
                        @Suppress("UNCHECKED_CAST")
                        val written = outputStream
                            .write(buffer.plus(offset) as CPointer<UByteVar>, (read - offset).convert())
                            .convert<Int>()
                        offset += written
                        if (written < 0) {
                            throw outputStream.streamError?.let { DarwinHttpRequestException(it) }
                                ?: inputStream.streamError?.let { DarwinHttpRequestException(it) }
                                ?: IOException("Failed to write to the network")
                        }
                    }
                }
            }
        } finally {
            outputStream.close()
        }
    }
    return inputStream
}

@OptIn(UnsafeNumber::class)
internal fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (isEmpty()) return@apply
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.convert())
    }
}

@OptIn(UnsafeNumber::class)
internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isEmpty()) return result

    result.usePinned {
        memcpy(it.addressOf(0), bytes, length)
    }

    return result
}

/**
 * Executes the given block function on this resource and then releases it correctly whether an
 * exception is thrown or not.
 */
internal inline fun <T : CPointed, R> CPointer<T>.use(block: (CPointer<T>) -> R): R {
    try {
        return block(this)
    } finally {
        CFBridgingRelease(this)
    }
}

@Suppress("KDocMissingDocumentation")
public class DarwinHttpRequestException(public val origin: NSError) : IOException("Exception in http request: $origin")
