@file:Suppress("unused", "UNUSED_PARAMETER")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*

/**
 * Write byte [value] repeated the specified [times].
 */
@Suppress("DEPRECATION")
public fun Buffer.fill(times: Int, value: Byte) {
    require(times >= 0) { "times shouldn't be negative: $times" }
    require(times <= writeRemaining) {
        "times shouldn't be greater than the write remaining space: $times > $writeRemaining"
    }

    memory.fill(writePosition, times, value)
    commitWritten(times)
}

/**
 * Write unsigned byte [value] repeated the specified [times].
 */
@Suppress("DEPRECATION")
public fun Buffer.fill(times: Int, value: UByte) {
    fill(times, value.toByte())
}

/**
 * Write byte [v] value repeated [n] times.
 */
@Deprecated("Use fill with n with type Int")
@Suppress("DEPRECATION")
public fun Buffer.fill(n: Long, v: Byte) {
    fill(n.toIntOrFail("n"), v)
}

@Suppress("DEPRECATION")
internal fun Buffer.appendChars(csq: CharSequence, start: Int = 0, end: Int = csq.length): Int {
    var charactersWritten: Int

    write { dst, dstStart, dstEndExclusive ->
        val result = dst.encodeUTF8(csq, start, end, dstStart, dstEndExclusive)
        charactersWritten = result.characters.toInt()
        result.bytes.toInt()
    }

    return start + charactersWritten
}

@Suppress("DEPRECATION")
public fun Buffer.readFully(dst: Array<Byte>, offset: Int = 0, length: Int = dst.size - offset) {
    read { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw EOFException("Not enough bytes available to read $length bytes")
        }

        for (index in 0 until length) {
            dst[index + offset] = memory[index + start]
        }

        length
    }
}
