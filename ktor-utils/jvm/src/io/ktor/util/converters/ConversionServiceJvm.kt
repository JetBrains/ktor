/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.converters

import java.math.*
import java.util.*
import kotlin.reflect.*

@OptIn(ExperimentalStdlibApi::class)
internal actual fun platformDefaultFromValues(value: String, klass: KClass<*>): Any? {
    val converted = convertSimpleTypes(value, klass)
    if (converted != null) {
        return converted
    }

    if (klass.java.isEnum) {
        return klass.java.enumConstants?.firstOrNull { (it as Enum<*>).name == value }
            ?: throw DataConversionException("Value $value is not a enum member name of $klass")
    }

    return null
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private fun convertSimpleTypes(value: String, klass: KClass<*>): Any? = when (klass) {
    Integer::class -> value.toInt()
    java.lang.Float::class -> value.toFloat()
    java.lang.Double::class -> value.toDouble()
    java.lang.Long::class -> value.toLong()
    java.lang.Short::class -> value.toShort()
    java.lang.Boolean::class -> value.toBoolean()
    java.lang.String::class -> value
    Character::class -> value[0]
    BigDecimal::class -> BigDecimal(value)
    BigInteger::class -> BigInteger(value)
    UUID::class -> UUID.fromString(value)
    else -> null
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal actual fun platformDefaultToValues(value: Any): List<String>? {
    if (value is Enum<*>) {
        return listOf(value.name)
    }
    return when (value) {
        is Integer -> listOf(value.toString())
        is java.lang.Float -> listOf(value.toString())
        is java.lang.Double -> listOf(value.toString())
        is java.lang.Long -> listOf(value.toString())
        is java.lang.Boolean -> listOf(value.toString())
        is java.lang.Short -> listOf(value.toString())
        is java.lang.String -> listOf(value.toString())
        is Character -> listOf(value.toString())
        is BigDecimal -> listOf(value.toString())
        is BigInteger -> listOf(value.toString())
        is UUID -> listOf(value.toString())
        else -> null
    }
}
