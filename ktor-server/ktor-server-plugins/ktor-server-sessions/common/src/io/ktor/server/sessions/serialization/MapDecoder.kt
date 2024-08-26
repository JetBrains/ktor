/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions.serialization

import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.modules.*

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
internal class MapDecoder(
    override val serializersModule: SerializersModule,
    string: String,
) : NamedValueDecoder() {
    private val parameters = parseQueryString(string, decode = true)

    private val parameterNames = parameters.names().toList()
    private val size: Int = parameterNames.size * 2
    private var position = -1

    override fun elementName(descriptor: SerialDescriptor, index: Int): String {
        val i = index / 2
        return parameterNames[i]
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (position < size - 1) {
            position++
            return position
        }
        return CompositeDecoder.DECODE_DONE
    }

    private fun currentElement(tag: String): String {
        val value = when {
            position % 2 == 0 -> tag
            else -> parameters[tag]!!
        }
        return when {
            value.startsWith("#bo") -> value.drop(3)
            value.startsWith("#ch") -> value.drop(3)
            else -> value.drop(2)
        }
    }

    override fun decodeTaggedBoolean(tag: String): Boolean {
        return currentElement(tag) == "t"
    }

    override fun decodeTaggedChar(tag: String): Char {
        return currentElement(tag)[0]
    }

    override fun decodeTaggedDouble(tag: String): Double {
        return currentElement(tag).toDouble()
    }

    override fun decodeTaggedFloat(tag: String): Float {
        return currentElement(tag).toFloat()
    }

    override fun decodeTaggedInt(tag: String): Int {
        return currentElement(tag).toInt()
    }

    override fun decodeTaggedLong(tag: String): Long {
        return currentElement(tag).toLong()
    }

    override fun decodeTaggedString(tag: String): String {
        return currentElement(tag)
    }

    override fun decodeTaggedNotNullMark(tag: String): Boolean {
        return currentElement(tag) != "#n"
    }

    override fun decodeTaggedNull(tag: String): Nothing? {
        return null
    }

    override fun decodeTaggedEnum(tag: String, enumDescriptor: SerialDescriptor): Int {
        val enumName = decodeTaggedString(tag)
        val index = enumDescriptor.getElementIndex(enumName)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw IllegalStateException(
                "${enumDescriptor.serialName} does not contain element with name '$enumName'"
            )
        }
        return index
    }
}
