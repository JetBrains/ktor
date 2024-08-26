/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import java.io.*

public fun InputStream.asInput(): Input = asSource().buffered()

public fun Source.inputStream(): InputStream = asInputStream()

@OptIn(InternalIoApi::class)
public fun OutputStream.writePacket(packet: Source) {
    packet.buffer.copyTo(this)
}

public fun OutputStream.writePacket(block: Sink.() -> Unit) {
    val builder = Buffer()
    builder.block()
    writePacket(builder)
}

@OptIn(UnsafeIoApi::class)
public fun InputStream.readPacketAtLeast(min: Int = 1): Source {
    val buffer = Buffer()
    UnsafeBufferOperations.writeToTail(buffer, min) { array, start, end ->
        val read = read(array, start, end - start)
        if (read < 0) 0 else read
    }

    return buffer
}
