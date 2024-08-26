/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.*
import java.security.*

internal fun Digest(): Digest = Digest(BytePacketBuilder())

@JvmInline
internal value class Digest(val state: Sink) : Closeable {

    fun update(packet: Source) = synchronized(state) {
        if (packet.exhausted()) return
        state.writePacket(packet.copy())
    }

    fun doHash(hashName: String): ByteArray = synchronized(state) {
        state.preview { handshakes: Source ->
            val digest = MessageDigest.getInstance(hashName)!!

            val buffer = DefaultByteBufferPool.borrow()
            try {
                while (!handshakes.exhausted()) {
                    val rc = handshakes.readAvailable(buffer)
                    if (rc == -1) break
                    buffer.flip()
                    digest.update(buffer)
                    buffer.clear()
                }

                return@preview digest.digest()
            } finally {
                DefaultByteBufferPool.recycle(buffer)
            }
        }
    }

    override fun close() {
        state.close()
    }
}

internal operator fun Digest.plusAssign(record: TLSHandshake) {
    check(record.type != TLSHandshakeType.HelloRequest)

    update(
        buildPacket {
            writeTLSHandshakeType(record.type, record.packet.remaining.toInt())
            if (record.packet.remaining > 0) writePacket(record.packet.copy())
        }
    )
}
