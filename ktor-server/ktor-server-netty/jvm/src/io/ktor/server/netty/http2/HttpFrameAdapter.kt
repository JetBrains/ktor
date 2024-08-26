/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http2

import io.ktor.utils.io.*
import io.netty.buffer.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.channels.*

internal suspend fun ReceiveChannel<Http2DataFrame>.http2frameLoop(bc: ByteWriteChannel) {
    try {
        while (true) {
            val message = receive()
            val content = message.content() ?: Unpooled.EMPTY_BUFFER

            while (content.readableBytes() > 0) {
                bc.write { bb ->
                    val size = content.readableBytes()
                    if (bb.remaining() > size) {
                        val l = bb.limit()
                        bb.limit(bb.position() + size)
                        content.readBytes(bb)
                        bb.limit(l)
                    } else {
                        content.readBytes(bb)
                    }
                }
            }

            bc.flush()
            content.release()

            if (message.isEndStream) {
                break
            }
        }
    } catch (closed: ClosedReceiveChannelException) {
    } catch (t: Throwable) {
        bc.close(t)
    } finally {
        bc.flushAndClose()
    }
}
