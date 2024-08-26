/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions.serialization

import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*

@OptIn(ExperimentalSerializationApi::class)
internal class SessionsBackwardCompatibleDecoder(
    override val serializersModule: SerializersModule,
    private val string: String,
) : AbstractDecoder() {

    private val parameters = parseQueryString(string, decode = true)

    private val parameterNames = parameters.names().iterator()
    private lateinit var currentName: String

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (!parameterNames.hasNext()) {
            return CompositeDecoder.DECODE_DONE
        }
        currentName = parameterNames.next()
        return descriptor.getElementIndex(currentName)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (!::currentName.isInitialized) return SessionsBackwardCompatibleDecoder(serializersModule, string)
        when (descriptor.kind) {
            StructureKind.LIST -> {
                val value = parameters[currentName]!!.drop(3).decodeURLQueryComponent()
                return ListLikeDecoder(serializersModule, value)
            }
            StructureKind.MAP -> {
                val encoded = parameters[currentName]!!.drop(2).decodeURLQueryComponent()
                val decoded = parseQueryString(encoded, decode = true)
                return MapDecoder(serializersModule, decoded.formUrlEncode())
            }
            StructureKind.CLASS -> {
                val value = parameters[currentName]!!.drop(2).decodeURLQueryComponent()
                return SessionsBackwardCompatibleDecoder(serializersModule, value)
            }
            else -> throw IllegalArgumentException("Unsupported kind: ${descriptor.kind}")
        }
    }

    override fun decodeBoolean(): Boolean {
        return parameters[currentName]!! == "#bot"
    }

    override fun decodeChar(): Char {
        return parameters[currentName]!![3]
    }

    override fun decodeDouble(): Double {
        return parameters[currentName]!!.drop(2).toDouble()
    }

    override fun decodeFloat(): Float {
        return parameters[currentName]!!.drop(2).toFloat()
    }

    override fun decodeInt(): Int {
        return parameters[currentName]!!.drop(2).toInt()
    }

    override fun decodeLong(): Long {
        return parameters[currentName]!!.drop(2).toLong()
    }

    override fun decodeString(): String {
        return parameters[currentName]!!.drop(2)
    }

    override fun decodeNotNullMark(): Boolean {
        return parameters[currentName]!! != "#n"
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
