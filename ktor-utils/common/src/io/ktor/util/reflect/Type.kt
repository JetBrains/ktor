/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

/**
 * Information about type.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.reflect.Type)
 */
@Deprecated("Not used anymore in common code as it was needed only for JVM target.")
public expect interface Type

@Suppress("DEPRECATION")
@Deprecated("Not used anymore in common code as it was needed only for JVM target.")
public expect val KType.platformType: Type

/**
 * Ktor type information.
 * @property type Source KClass<*>
 * @property kotlinType Kotlin reified type with all generic type parameters.
 */
public class TypeInfo(
    public val type: KClass<*>,
    public val kotlinType: KType? = null
) {

    @Suppress("UNUSED_PARAMETER", "DEPRECATION")
    @Deprecated("Use constructor without reifiedType parameter.", ReplaceWith("TypeInfo(type, kotlinType)"))
    public constructor(
        type: KClass<*>,
        reifiedType: Type,
        kotlinType: KType? = null,
    ) : this(type, kotlinType)

    override fun hashCode(): Int {
        return kotlinType?.hashCode() ?: type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeInfo) return false

        return if (kotlinType != null || other.kotlinType != null) {
            kotlinType == other.kotlinType
        } else {
            type == other.type
        }
    }

    override fun toString(): String = "TypeInfo(${kotlinType ?: type})"
}

/**
 * Returns [TypeInfo] for the specified type [T]
 */
public inline fun <reified T> typeInfo(): TypeInfo = TypeInfo(T::class, typeOfOrNull<T>())

/**
 * Check [this] is instance of [type].
 */
public expect fun Any.instanceOf(type: KClass<*>): Boolean

@PublishedApi
internal inline fun <reified T> typeOfOrNull(): KType? = try {
    // We need to wrap getting a type in try catch because of:
    // - KT-42913
    // - KTOR-7479 (R8 in full mode strips class signatures)
    typeOf<T>()
} catch (_: Throwable) {
    null
}
