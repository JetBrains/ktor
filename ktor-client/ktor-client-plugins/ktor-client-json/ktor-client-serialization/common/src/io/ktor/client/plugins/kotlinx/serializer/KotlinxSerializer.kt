/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package io.ktor.client.plugins.kotlinx.serializer

import io.ktor.client.plugins.json.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

/**
 * A [JsonSerializer] implemented for kotlinx [Serializable] classes.
 */
@Deprecated(
    "Please use ContentNegotiation plugin and its converters: https://ktor.io/docs/migrating-2.html#serialization-client", // ktlint-disable max-line-length
    level = DeprecationLevel.ERROR
)
public class KotlinxSerializer(
    private val json: Json = DefaultJson
) : JsonSerializer {

    override fun write(data: Any, contentType: ContentType): OutgoingContent {
        @Suppress("UNCHECKED_CAST")
        return TextContent(writeContent(data), contentType)
    }

    internal fun writeContent(data: Any): String =
        json.encodeToString(buildSerializer(data, json.serializersModule), data)

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override fun read(type: TypeInfo, body: Input): Any {
        val text = body.readText()
        val deserializationStrategy = json.serializersModule.getContextual(type.type)
        val mapper = deserializationStrategy ?: (type.kotlinType?.let { serializer(it) } ?: type.type.serializer())
        return json.decodeFromString(mapper, text)!!
    }

    public companion object {
        /**
         * Default [Json] configuration for [KotlinxSerializer].
         */
        public val DefaultJson: Json = Json {
            isLenient = false
            ignoreUnknownKeys = false
            allowSpecialFloatingPointValues = true
            useArrayPolymorphism = false
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun buildSerializer(value: Any, module: SerializersModule): KSerializer<Any> = when (value) {
    is JsonElement -> JsonElement.serializer()
    is List<*> -> ListSerializer(value.elementSerializer(module))
    is Array<*> -> value.firstOrNull()?.let { buildSerializer(it, module) } ?: ListSerializer(String.serializer())
    is Set<*> -> SetSerializer(value.elementSerializer(module))
    is Map<*, *> -> {
        val keySerializer = value.keys.elementSerializer(module)
        val valueSerializer = value.values.elementSerializer(module)
        MapSerializer(keySerializer, valueSerializer)
    }
    else -> {
        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        module.getContextual(value::class) ?: value::class.serializer()
    }
} as KSerializer<Any>

@OptIn(ExperimentalSerializationApi::class)
private fun Collection<*>.elementSerializer(module: SerializersModule): KSerializer<*> {
    val serializers: List<KSerializer<*>> =
        filterNotNull().map { buildSerializer(it, module) }.distinctBy { it.descriptor.serialName }

    if (serializers.size > 1) {
        error(
            "Serializing collections of different element types is not yet supported. " +
                "Selected serializers: ${serializers.map { it.descriptor.serialName }}"
        )
    }

    val selected = serializers.singleOrNull() ?: String.serializer()

    if (selected.descriptor.isNullable) {
        return selected
    }

    @Suppress("UNCHECKED_CAST")
    selected as KSerializer<Any>

    if (any { it == null }) {
        return selected.nullable
    }

    return selected
}
