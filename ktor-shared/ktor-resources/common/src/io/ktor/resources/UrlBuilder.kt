/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources

import io.ktor.http.*
import io.ktor.resources.serialization.*
import io.ktor.util.*
import kotlinx.serialization.*

/**
 * Constructs a URL for the [resource].
 *
 * The class of the [resource] instance **must** be annotated with [Resource].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.resources.href)
 */
public inline fun <reified T> href(
    resourcesFormat: ResourcesFormat,
    resource: T,
    urlBuilder: URLBuilder
) {
    val serializer = serializer<T>()
    href(resourcesFormat, serializer, resource, urlBuilder)
}

/**
 * Constructs a URL for the [resource].
 *
 * The class of the [resource] instance **must** be annotated with [Resource].
 */
public inline fun <reified T> href(
    resourcesFormat: ResourcesFormat,
    resource: T,
): String {
    val urlBuilder = URLBuilder()
    href(resourcesFormat, resource, urlBuilder)
    return urlBuilder.build().fullPath
}

public fun <T> href(
    resourcesFormat: ResourcesFormat,
    serializer: KSerializer<T>,
    resource: T,
    urlBuilder: URLBuilder
) {
    val parameters = resourcesFormat.encodeToParameters(serializer, resource)
    val pathPattern = resourcesFormat.encodeToPathPattern(serializer)

    val usedForPathParameterNames = mutableSetOf<String>()
    val pathParts = pathPattern.split("/")

    val updatedParts = pathParts.flatMap {
        if (!it.startsWith('{') || !it.endsWith('}')) return@flatMap listOf(it)

        val part = it.substring(1, it.lastIndex)
        when {
            part.endsWith('?') -> {
                val values = parameters.getAll(part.dropLast(1)) ?: return@flatMap emptyList()
                if (values.size > 1) {
                    throw ResourceSerializationException(
                        "Expect zero or one parameter with name: ${part.dropLast(1)}, but found ${values.size}"
                    )
                }
                usedForPathParameterNames += part.dropLast(1)
                values
            }
            part.endsWith("...") -> {
                usedForPathParameterNames += part.dropLast(3)
                parameters.getAll(part.dropLast(3)) ?: emptyList()
            }
            else -> {
                val values = parameters.getAll(part)
                if (values == null || values.size != 1) {
                    throw ResourceSerializationException(
                        "Expect exactly one parameter with name: $part, but found ${values?.size ?: 0}"
                    )
                }
                usedForPathParameterNames += part
                values
            }
        }
    }

    urlBuilder.pathSegments = updatedParts

    val queryArgs = parameters.filter { key, _ -> !usedForPathParameterNames.contains(key) }
    urlBuilder.parameters.appendAll(queryArgs)
}
