package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import org.khronos.webgl.*

@Suppress("DEPRECATION")
internal fun encodeISO88591(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Buffer): Int {
    if (fromIndex >= toIndex) return 0

    dst.writeDirect { view ->
        val i8 = Int8Array(view.buffer, view.byteOffset, view.byteLength)
        var writeIndex = 0
        for (index in fromIndex until toIndex) {
            val character = input[index].code
            if (character > 0xff) {
                failedToMapError(character)
            }
            i8[writeIndex++] = character.toByte()
        }
        writeIndex
    }
    return toIndex - fromIndex
}

private fun failedToMapError(ch: Int): Nothing {
    throw MalformedInputException("The character with unicode point $ch couldn't be mapped to ISO-8859-1 character")
}
