/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/**
 * Represents HTTP parameters as a map from case-insensitive names to collection of [String] values
 */
public interface Parameters : StringValues {
    public companion object {
        /**
         * Empty [Parameters] instance
         */
        public val Empty: Parameters = EmptyParameters

        /**
         * Builds a [Parameters] instance with the given [builder] function
         * @param builder specifies a function to build a map
         */
        public inline fun build(builder: ParametersBuilder.() -> Unit): Parameters =
            ParametersBuilder().apply(builder).build()
    }
}

public interface ParametersBuilder : StringValuesBuilder {
    override fun build(): Parameters
}

public fun ParametersBuilder(size: Int = 8): ParametersBuilder = ParametersBuilderImpl(size)

public class ParametersBuilderImpl(
    size: Int = 8
) : StringValuesBuilderImpl(true, size), ParametersBuilder {
    override fun build(): Parameters {
        return ParametersImpl(values)
    }
}

/**
 * Returns an empty [Parameters] instance
 */
public fun parametersOf(): Parameters = Parameters.Empty

/**
 * Creates a [Parameters] instance containing only single pair
 */
public fun parametersOf(name: String, value: String): Parameters = ParametersSingleImpl(name, listOf(value))

/**
 * Creates a [Parameters] instance containing only single pair of [name] with multiple [values]
 */
public fun parametersOf(name: String, values: List<String>): Parameters = ParametersSingleImpl(name, values)

/**
 * Creates a [Parameters] instance from the entries of the given [map]
 */
public fun parametersOf(map: Map<String, List<String>>): Parameters = ParametersImpl(map)

/**
 * Creates a [Parameters] instance from the specified [pairs]
 */
public fun parametersOf(vararg pairs: Pair<String, List<String>>): Parameters = ParametersImpl(pairs.asList().toMap())

/**
 * Builds a [Parameters] instance with the given [builder] function
 * @param builder specifies a function to build a map
 */
public fun parameters(builder: ParametersBuilder.() -> Unit): Parameters = Parameters.build(builder)

public class ParametersImpl(
    values: Map<String, List<String>> = emptyMap()
) : Parameters, StringValuesImpl(true, values) {
    override fun toString(): String = "Parameters ${entries()}"
}

public class ParametersSingleImpl(name: String, values: List<String>) : Parameters,
    StringValuesSingleImpl(true, name, values) {
    override fun toString(): String = "Parameters ${entries()}"
}

/**
 * Plus operator function that creates a new parameters instance from the original one concatenating with [other]
 */
public operator fun Parameters.plus(other: Parameters): Parameters = when {
    caseInsensitiveName == other.caseInsensitiveName -> when {
        this.isEmpty() -> other
        other.isEmpty() -> this
        else -> Parameters.build { appendAll(this@plus); appendAll(other) }
    }

    else -> {
        throw IllegalArgumentException(
            "Cannot concatenate Parameters with case-sensitive and case-insensitive names"
        )
    }
}

internal object EmptyParameters : Parameters {
    override val caseInsensitiveName: Boolean get() = true
    override fun getAll(name: String): List<String>? = null
    override fun names(): Set<String> = emptySet()
    override fun entries(): Set<Map.Entry<String, List<String>>> = emptySet()
    override fun isEmpty(): Boolean = true
    override fun toString(): String = "Parameters ${entries()}"

    override fun equals(other: Any?): Boolean = other is Parameters && other.isEmpty()
}
