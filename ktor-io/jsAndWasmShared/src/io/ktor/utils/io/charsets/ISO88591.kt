package io.ktor.utils.io.charsets

import kotlinx.io.*

internal fun encodeISO88591(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Sink): Int {
    if (fromIndex >= toIndex) return 0

    for (index in fromIndex until toIndex) {
        val character = input[index].code
        if (character > 0xff) {
            failedToMapError(character)
        }
        dst.writeByte(character.toByte())
    }
    return toIndex - fromIndex
}

private fun failedToMapError(ch: Int): Nothing {
    throw MalformedInputException("The character with unicode point $ch couldn't be mapped to ISO-8859-1 character")
}
