/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlin.jvm.*
import kotlin.reflect.*

/**
 * Specifies a key for an attribute in [Attributes]
 * @param T is a type of the value stored in the attribute
 * @param name is a name of the attribute for diagnostic purposes. Can't be blank
 */
@JvmSynthetic
public inline fun <reified T : Any> AttributeKey(name: String): AttributeKey<T> =
    AttributeKey(name, typeOf<T>())

/**
 * Specifies a key for an attribute in [Attributes]
 * @param T is a type of the value stored in the attribute
 * @property name is a name of the attribute for diagnostic purposes. Can't be blank
 * @property type the recorded kotlin type of T
 */
public data class AttributeKey<T : Any> @JvmOverloads constructor(
    public val name: String,
    private val type: KType = typeOf<Any>(),
) {
    init {
        if (name.isEmpty()) {
            throw IllegalStateException("Name can't be blank")
        }
    }

    override fun toString(): String = "AttributeKey: $name"
}

/**
 * A version of [AttributeKey] that overrides [equals] and [hashCode] using [name]
 * @param T is a type of the value stored in the attribute
 * @param name is a name of the attribute
 */
@Deprecated(
    "Please use `AttributeKey` class instead",
    replaceWith = ReplaceWith("AttributeKey"),
    level = DeprecationLevel.ERROR
)
public typealias EquatableAttributeKey<T> = AttributeKey<T>

/**
 * Creates an attributes instance suitable for the particular platform
 */
public expect fun Attributes(concurrent: Boolean = false): Attributes

/**
 * Map of attributes accessible by [AttributeKey] in a typed manner
 */
public interface Attributes {
    /**
     * Gets a value of the attribute for the specified [key], or throws an exception if an attribute doesn't exist
     */
    public operator fun <T : Any> get(key: AttributeKey<T>): T =
        getOrNull(key) ?: throw IllegalStateException("No instance for key $key")

    /**
     * Gets a value of the attribute for the specified [key], or return `null` if an attribute doesn't exist
     */
    public fun <T : Any> getOrNull(key: AttributeKey<T>): T?

    /**
     * Checks if an attribute with the specified [key] exists
     */
    public operator fun contains(key: AttributeKey<*>): Boolean

    /**
     * Creates or changes an attribute with the specified [key] using [value]
     */
    public fun <T : Any> put(key: AttributeKey<T>, value: T)

    /**
     * Removes an attribute with the specified [key]
     */
    public fun <T : Any> remove(key: AttributeKey<T>)

    /**
     * Removes an attribute with the specified [key] and returns its current value, throws an exception if an attribute doesn't exist
     */
    public fun <T : Any> take(key: AttributeKey<T>): T = get(key).also { remove(key) }

    /**
     * Removes an attribute with the specified [key] and returns its current value, returns `null` if an attribute doesn't exist
     */
    public fun <T : Any> takeOrNull(key: AttributeKey<T>): T? = getOrNull(key).also { remove(key) }

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value
     */
    public fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T

    /**
     * Returns [List] of all [AttributeKey] instances in this map
     */
    public val allKeys: List<AttributeKey<*>>
}

/**
 * Adds all attributes from another collection, replacing original values if any.
 */
public fun Attributes.putAll(other: Attributes) {
    other.allKeys.forEach {
        @Suppress("UNCHECKED_CAST")
        put(it as AttributeKey<Any>, other[it])
    }
}
