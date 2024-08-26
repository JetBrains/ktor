/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.cipher

import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.io.*
import java.nio.*
import javax.crypto.*

internal val CryptoBufferPool: ObjectPool<ByteBuffer> = ByteBufferPool(128, 65536)

internal fun Source.cipherLoop(cipher: Cipher, header: Sink.() -> Unit = {}): Source {
    val srcBuffer = DefaultByteBufferPool.borrow()
    var dstBuffer = CryptoBufferPool.borrow()
    var dstBufferFromPool = true

    try {
        return buildPacket {
            srcBuffer.clear()
            header()

            while (true) {
                val rc = if (srcBuffer.hasRemaining()) readAvailable(srcBuffer) else 0
                srcBuffer.flip()

                if (!srcBuffer.hasRemaining() && (rc == -1 || this@cipherLoop.exhausted())) break

                dstBuffer.clear()

                if (cipher.getOutputSize(srcBuffer.remaining()) > dstBuffer.remaining()) {
                    if (dstBufferFromPool) {
                        CryptoBufferPool.recycle(dstBuffer)
                    }
                    dstBuffer = ByteBuffer.allocate(cipher.getOutputSize(srcBuffer.remaining()))
                    dstBufferFromPool = false
                }

                cipher.update(srcBuffer, dstBuffer)
                dstBuffer.flip()
                writeFully(dstBuffer)
                srcBuffer.compact()
            }

            assert(!srcBuffer.hasRemaining()) { "Cipher loop completed too early: there are unprocessed bytes" }
            assert(!dstBuffer.hasRemaining()) { "Not all bytes were appended to the packet" }

            val requiredBufferSize = cipher.getOutputSize(0)
            if (requiredBufferSize == 0) return@buildPacket
            if (requiredBufferSize > dstBuffer.capacity()) {
                writeFully(cipher.doFinal())
                return@buildPacket
            }

            dstBuffer.clear()
            cipher.doFinal(EmptyByteBuffer, dstBuffer)
            dstBuffer.flip()

            if (!dstBuffer.hasRemaining()) { // workaround JDK bug
                writeFully(cipher.doFinal())
                return@buildPacket
            }

            writeFully(dstBuffer)
        }
    } finally {
        DefaultByteBufferPool.recycle(srcBuffer)
        if (dstBufferFromPool) {
            CryptoBufferPool.recycle(dstBuffer)
        }
    }
}
