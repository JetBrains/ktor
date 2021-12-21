package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import kotlin.native.concurrent.*

public actual val PACKET_MAX_COPY_SIZE: Int = 200

internal const val BUFFER_VIEW_POOL_SIZE = 1024

internal const val BUFFER_VIEW_SIZE = 4096

public actual fun BytePacketBuilder(headerSizeHint: Int): BytePacketBuilder =
    BytePacketBuilder(headerSizeHint, ChunkBuffer.Pool)

public actual typealias EOFException = io.ktor.utils.io.errors.EOFException
