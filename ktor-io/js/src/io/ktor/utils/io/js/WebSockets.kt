package io.ktor.utils.io.js

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import org.khronos.webgl.*
import org.w3c.dom.*

public fun WebSocket.sendPacket(packet: ByteReadPacket) {
    send(packet.readArrayBuffer())
}

public inline fun WebSocket.sendPacket(block: BytePacketBuilder.() -> Unit) {
    sendPacket(buildPacket(block = block))
}

@Suppress("DEPRECATION")
public inline fun MessageEvent.packet(): ByteReadPacket {
    @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE", "UnsafeCastFromDynamic")
    return ByteReadPacket(
        ChunkBuffer(Memory(data.asDynamic() as DataView), null, ChunkBuffer.NoPool),
        ChunkBuffer.NoPool
    )
}
