/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

public interface BufferedByteWriteChannel : ByteWriteChannel {
    /**
     * Flush all pending bytes from [writeBuffer] to the internal read buffer without suspension.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.BufferedByteWriteChannel.flushWriteBuffer)
     */
    @InternalAPI
    public fun flushWriteBuffer()

    /**
     * Flush all pending bytes from [writeBuffer] to the internal read buffer without suspension and initiate channel close.
     */
    public fun close()
}
