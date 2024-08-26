/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlinx.io.*
import java.nio.*

@OptIn(InternalIoApi::class)
public fun Sink.writeFully(buffer: ByteBuffer) {
    this.buffer.transferFrom(buffer)
}
