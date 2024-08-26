@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package io.ktor.utils.io.core

import kotlinx.io.*
import java.nio.*

public fun Sink.writeByteBuffer(bb: ByteBuffer) {
    write(bb)
}
