/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal.legacy

import io.ktor.client.call.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.*
import platform.Foundation.*
import platform.posix.*

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun OutgoingContent.toNSData(): NSData? = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().toNSData()
    is OutgoingContent.WriteChannelContent -> GlobalScope.writer(Dispatchers.Unconfined) {
        writeTo(channel)
    }.channel.readRemaining().readByteArray().toNSData()
    is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readByteArray().toNSData()
    is OutgoingContent.NoContent -> null
    is OutgoingContent.ContentWrapper -> delegate().toNSData()
    is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (isEmpty()) return@apply
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.convert())
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
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
@OptIn(ExperimentalForeignApi::class)
internal inline fun <T : CPointed, R> CPointer<T>.use(block: (CPointer<T>) -> R): R {
    try {
        return block(this)
    } finally {
        CFBridgingRelease(this)
    }
}
