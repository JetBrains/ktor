/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources.serialization

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.modules.*

/**
 * A format to (de)serialize resources instances
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.resources.serialization.ResourcesFormat)
 */
@OptIn(ExperimentalSerializationApi::class)
public class ResourcesFormat(
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : SerialFormat {

    /**
     * A query parameter description
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.resources.serialization.ResourcesFormat.Parameter)
     */
    public data class Parameter(
        val name: String,
        val isOptional: Boolean
    )

    /**
     * Builds a path pattern for a given [serializer]
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.resources.serialization.ResourcesFormat.encodeToPathPattern)
     */
    public fun <T> encodeToPathPattern(serializer: KSerializer<T>): String {
        val pathBuilder = StringBuilder()

        var current: SerialDescriptor? = serializer.descriptor
        while (current != null) {
            val path = current.annotations.filterIsInstance<Resource>().first().path
            val addSlash = pathBuilder.isNotEmpty() && !pathBuilder.startsWith('/') && !path.endsWith('/')
            if (addSlash) {
                pathBuilder.insert(0, '/')
            }
            pathBuilder.insert(0, path)

            val membersWithAnnotations = current.elementDescriptors.filter { it.annotations.any { it is Resource } }
            if (membersWithAnnotations.size > 1) {
                throw ResourceSerializationException("There are multiple parents for resource ${current.serialName}")
            }
            current = membersWithAnnotations.firstOrNull()
        }

        if (pathBuilder.startsWith('/')) {
            pathBuilder.deleteAt(0)
        }
        return pathBuilder.toString()
    }

    /**
     * Builds a description of query parameters for a given [serializer]
     */
    public fun <T> encodeToQueryParameters(serializer: KSerializer<T>): Set<Parameter> {
        val path = encodeToPathPattern(serializer)

        val allParameters = mutableSetOf<Parameter>()
        collectAllParameters(serializer.descriptor, allParameters)

        return allParameters
            .filterNot { (name, _) ->
                path.contains("{$name}") || path.contains("{$name?}") || path.contains("{$name...}")
            }
            .toSet()
    }

    private fun collectAllParameters(descriptor: SerialDescriptor, result: MutableSet<Parameter>) {
        descriptor.elementNames.forEach { name ->
            val index = descriptor.getElementIndex(name)
            val elementDescriptor = descriptor.getElementDescriptor(index)
            if (!elementDescriptor.isInline && elementDescriptor.kind is StructureKind.CLASS) {
                collectAllParameters(elementDescriptor, result)
            } else {
                result.add(Parameter(name, descriptor.isElementOptional(index)))
            }
        }
    }

    /**
     * Builds [Parameters] for a resource [T]
     */
    public fun <T> encodeToParameters(serializer: KSerializer<T>, value: T): Parameters {
        val encoder = ParametersEncoder(serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return encoder.parameters
    }

    /**
     * Builds a [T] resource instance from [parameters]
     */
    public fun <T> decodeFromParameters(deserializer: KSerializer<T>, parameters: Parameters): T {
        val input = ParametersDecoder(serializersModule, parameters, emptyList())
        return input.decodeSerializableValue(deserializer)
    }
}
