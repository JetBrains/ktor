/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*

@OptIn(ExperimentalSerializationApi::class)
internal class ListLikeDecoder(
    override val serializersModule: SerializersModule,
    string: String,
) : AbstractDecoder() {

    private var currentIndex = -1
    private val items = string.split("&")

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (++currentIndex == items.size) {
            return CompositeDecoder.DECODE_DONE
        }
        return currentIndex
    }

    override fun decodeBoolean(): Boolean {
        return items[currentIndex] == "#bot"
    }

    override fun decodeChar(): Char {
        return items[currentIndex][3]
    }

    override fun decodeDouble(): Double {
        return items[currentIndex].drop(2).toDouble()
    }

    override fun decodeFloat(): Float {
        return items[currentIndex].drop(2).toFloat()
    }

    override fun decodeInt(): Int {
        return items[currentIndex].drop(2).toInt()
    }

    override fun decodeLong(): Long {
        return items[currentIndex].drop(2).toLong()
    }

    override fun decodeString(): String {
        return items[currentIndex].drop(2)
    }

    override fun decodeNotNullMark(): Boolean {
        return items[currentIndex] != "#n"
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val enumName = decodeString()
        val index = enumDescriptor.getElementIndex(enumName)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw IllegalStateException(
                "${enumDescriptor.serialName} does not contain element with name '$enumName'"
            )
        }
        return index
    }
}
