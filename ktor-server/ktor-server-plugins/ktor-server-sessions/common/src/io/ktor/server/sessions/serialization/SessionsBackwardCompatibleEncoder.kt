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
internal class SessionsBackwardCompatibleEncoder(
    override val serializersModule: SerializersModule
) : AbstractEncoder() {

    private val parametersBuilder = ParametersBuilder()

    private lateinit var nextElementName: String

    private var currentClassEncoder: SessionsBackwardCompatibleEncoder? = null
    private var currentList: MutableList<String>? = null
    private var currentMap: MutableMap<String, String>? = null
    private var mapKey: String? = null

    fun result(): String {
        if (currentClassEncoder != null) {
            return currentClassEncoder!!.result()
        }
        return parametersBuilder.build().formUrlEncode()
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        when (descriptor.kind) {
            StructureKind.LIST -> currentList = mutableListOf()
            StructureKind.MAP -> currentMap = mutableMapOf()
            StructureKind.CLASS, PolymorphicKind.SEALED -> {
                currentClassEncoder = SessionsBackwardCompatibleEncoder(serializersModule)
                return currentClassEncoder!!
            }
            else -> throw IllegalArgumentException("Unsupported kind: ${descriptor.kind}")
        }
        return super.beginStructure(descriptor)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (currentClassEncoder != null) {
            val encoded = currentClassEncoder!!.result()
            parametersBuilder.append(nextElementName, "##$encoded")
            currentClassEncoder = null
        } else if (currentList != null) {
            val encoded = currentList!!
                .joinToString("&") { it.encodeURLQueryComponent() }
                .encodeURLQueryComponent()
            parametersBuilder.append(nextElementName, "#cl$encoded")
            currentList = null
        } else if (currentMap != null) {
            val encoded = currentMap!!
                .map { (key, value) -> "${key.encodeURLQueryComponent()}=${value.encodeURLQueryComponent()}" }
                .joinToString("&")
                .encodeURLQueryComponent()
            parametersBuilder.append(nextElementName, "#m$encoded")
            currentMap = null
        }
        super.endStructure(descriptor)
    }

    override fun encodeValue(value: Any) {
        val encoded = primitiveValue(value) ?: return
        if (currentList != null) {
            currentList!!.add(encoded)
            return
        } else if (currentMap != null) {
            if (mapKey != null) {
                currentMap!![mapKey!!] = encoded
                mapKey = null
            } else {
                mapKey = encoded
            }
            return
        }
        parametersBuilder.append(nextElementName, encoded)
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        if (descriptor.kind != StructureKind.LIST && descriptor.kind != StructureKind.MAP) {
            nextElementName = descriptor.getElementName(index)
        }
        return true
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        encodeValue(enumDescriptor.getElementName(index))
    }

    override fun encodeNull() {
        parametersBuilder.append(nextElementName, "#n")
    }

    private fun primitiveValue(value: Any) = when (value) {
        is Int -> "#i$value"
        is Long -> "#l$value"
        is Float -> "#f$value"
        is Double -> "#f$value"
        is Boolean -> "#bo${value.toString().first()}"
        is Char -> "#ch$value"
        is String -> "#s$value"
        is Enum<*> -> "#s${value.name}"
        else -> null
    }
}
