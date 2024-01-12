@file:Suppress("FunctionName")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
public actual fun ByteReadPacket(
    array: ByteArray,
    offset: Int,
    length: Int,
    block: (ByteArray) -> Unit
): ByteReadPacket {
    if (length == 0) {
        block(array)
        return ByteReadPacket.Empty
    }

    @Suppress("DEPRECATION")
    val pool = object : SingleInstancePool<ChunkBuffer>() {
        private var pinned: Pinned<*>? = null

        override fun produceInstance(): ChunkBuffer {
            check(pinned == null) { "This implementation can pin only once." }

            val content = array.pin()
            val base = content.addressOf(offset)
            pinned = content

            return ChunkBuffer(Memory(base, length), null, this)
        }

        override fun disposeInstance(instance: ChunkBuffer) {
            check(pinned != null) { "The array hasn't been pinned yet" }
            block(array)
            pinned?.unpin()
            pinned = null
        }
    }

    return ByteReadPacket(pool.borrow().apply { resetForRead() }, pool)
}
