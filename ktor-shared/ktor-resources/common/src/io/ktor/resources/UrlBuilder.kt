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
    val actualParameterValues = resourcesFormat.encodeToParameters(serializer, resource)
    val path = resourcesFormat.encodeToPathPattern(serializer)

    val parameterNamesFoundInPath = mutableSetOf<String>()

    val newPath = path.replace(parameterRegex) { matchResult ->
        val foundParameterResult = matchResult.groups[1]!!
        val foundParameterName = foundParameterResult.value

        val isOptional = matchResult.groups[3] != null
        val isWildcard = matchResult.groups[4] != null

        when {
            isOptional -> {
                val foundParameterValues = actualParameterValues.getAll(foundParameterName)
                if (foundParameterValues != null && foundParameterValues.size > 1) {
                    throw ResourceSerializationException(
                        "Expect zero or one parameter with name: $foundParameterName, " +
                            "but found ${foundParameterValues.size}"
                    )
                }
                parameterNamesFoundInPath += foundParameterName
                foundParameterValues?.get(0) ?: NO_PARAMETER_FOUND
            }

            isWildcard -> {
                parameterNamesFoundInPath += foundParameterName
                val foundParameterValues = actualParameterValues.getAll(foundParameterName)
                if (foundParameterValues != null) {
                    foundParameterValues.joinToString("/")
                } else {
                    val emptyGroup = path.substring(matchResult.range.first - 1, matchResult.range.last + 1)
                    if (emptyGroup.startsWith("/")) {
                        NO_PARAMETER_FOUND
                    } else {
                        ""
                    }
                }
            }

            else -> {
                val values = actualParameterValues.getAll(foundParameterName)
                if (values == null || values.size != 1) {
                    throw ResourceSerializationException(
                        "Expect exactly one parameter with name: $foundParameterName, but found ${values?.size ?: 0}"
                    )
                }
                parameterNamesFoundInPath += foundParameterName
                values.joinToString("/")
            }
        }
    }

    urlBuilder.path(newPath.replace("/$NO_PARAMETER_FOUND", ""))
    val queryArgs = actualParameterValues.filter { key, _ -> key !in parameterNamesFoundInPath }
    urlBuilder.parameters.appendAll(queryArgs)
}

private val parameterRegex = """\{([^}]\w*)((\?)|(\.\.\.))?\}""".toRegex()
private const val NO_PARAMETER_FOUND = "<NO-PARAMETER-FOUND>"
