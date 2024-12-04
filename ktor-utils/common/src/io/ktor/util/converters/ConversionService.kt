/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.converters

import io.ktor.util.reflect.*
import kotlin.reflect.*

/**
 * Data conversion service that does serialization and deserialization to/from list of strings
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.converters.ConversionService)
 */
public interface ConversionService {
    /**
     * Deserialize [values] to an instance of [type]
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.converters.ConversionService.fromValues)
     */
    public fun fromValues(values: List<String>, type: TypeInfo): Any?

    /**
     * Serialize a [value] to values list
     */
    public fun toValues(value: Any?): List<String>
}

/**
 * The default conversion service that supports only basic types and enums
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.converters.DefaultConversionService)
 */
public object DefaultConversionService : ConversionService {
    override fun toValues(value: Any?): List<String> {
        if (value == null) {
            return emptyList()
        }
        val converted = platformDefaultToValues(value)
        if (converted != null) {
            return converted
        }
        return when (value) {
            is Iterable<*> -> value.flatMap { toValues(it) }
            else -> {
                when (val klass = value::class) {
                    Int::class,
                    Float::class,
                    Double::class,
                    Long::class,
                    Short::class,
                    Char::class,
                    Boolean::class,
                    String::class -> listOf(value.toString())
                    else -> throw DataConversionException(
                        "Class $klass is not supported in default data conversion service"
                    )
                }
            }
        }
    }

    override fun fromValues(values: List<String>, type: TypeInfo): Any? {
        if (values.isEmpty()) {
            return null
        }

        if (type.type == List::class || type.type == MutableList::class) {
            val argumentType = type.kotlinType?.arguments?.single()?.type?.classifier as? KClass<*>
            if (argumentType != null) {
                return values.map { fromValue(it, argumentType) }
            }
        }

        when {
            values.isEmpty() ->
                throw DataConversionException("There are no values when trying to construct single value $type")
            values.size > 1 ->
                throw DataConversionException("There are multiple values when trying to construct single value $type")
            else -> return fromValue(values.single(), type.type)
        }
    }

    public fun fromValue(value: String, klass: KClass<*>): Any {
        val converted = convertPrimitives(klass, value)
        if (converted != null) {
            return converted
        }

        val platformConverted = platformDefaultFromValues(value, klass)
        if (platformConverted != null) {
            return platformConverted
        }

        throwConversionException(klass.toString())
    }

    private fun convertPrimitives(klass: KClass<*>, value: String) = when (klass) {
        Int::class -> value.toInt()
        Float::class -> value.toFloat()
        Double::class -> value.toDouble()
        Long::class -> value.toLong()
        Short::class -> value.toShort()
        Char::class -> value.single()
        Boolean::class -> value.toBoolean()
        String::class -> value
        else -> null
    }

    private fun throwConversionException(typeName: String): Nothing {
        throw DataConversionException("Type $typeName is not supported in default data conversion service")
    }
}

internal expect fun platformDefaultFromValues(value: String, klass: KClass<*>): Any?

internal expect fun platformDefaultToValues(value: Any): List<String>?

/**
 * Thrown when failed to convert value
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.converters.DataConversionException)
 */
public open class DataConversionException(message: String = "Invalid data format") : Exception(message)
