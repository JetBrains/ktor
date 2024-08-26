/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.util

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.util.converters.*
import io.ktor.util.reflect.*
import kotlin.reflect.*

/**
 * Operator function that allows to delegate variables by call parameters.
 * It does conversion to type [R] using [DefaultConversionService]
 *
 * Example
 *
 * ```
 * get("/") {
 *     val page: Int by call.request.queryParameters
 *     val query: String by call.request.queryParameters
 *     // ...
 * }
 * ```
 *
 * @throws MissingRequestParameterException if no values associated with name
 * @throws ParameterConversionException when conversion from String to [R] fails
 */
public inline operator fun <reified R : Any> Parameters.getValue(thisRef: Any?, property: KProperty<*>): R {
    return getOrFail<R>(property.name)
}

/**
 * Get parameters value associated with this [name] or fail with [MissingRequestParameterException]
 * @throws MissingRequestParameterException if no values associated with this [name]
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun Parameters.getOrFail(name: String): String {
    return get(name) ?: throw MissingRequestParameterException(name)
}

/**
 * Get parameters value associated with this [name] converting to type [R] using [DefaultConversionService]
 * or fail with [MissingRequestParameterException]
 * @throws MissingRequestParameterException if no values associated with this [name]
 * @throws ParameterConversionException when conversion from String to [R] fails
 */
@OptIn(ExperimentalStdlibApi::class)
public inline fun <reified R : Any> Parameters.getOrFail(name: String): R {
    return getOrFailImpl(name, typeInfo<R>())
}

@PublishedApi
internal fun <R : Any> Parameters.getOrFailImpl(name: String, typeInfo: TypeInfo): R {
    val values = getAll(name) ?: throw MissingRequestParameterException(name)
    return try {
        @Suppress("UNCHECKED_CAST")
        DefaultConversionService.fromValues(values, typeInfo) as R
    } catch (cause: Exception) {
        throw ParameterConversionException(name, typeInfo.type.simpleName ?: typeInfo.type.toString(), cause)
    }
}
