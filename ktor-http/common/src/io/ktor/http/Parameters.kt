/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        @Suppress("DEPRECATION_ERROR")
        public val Empty: Parameters = EmptyParameters

        /**
         * Builds a [Parameters] instance with the given [builder] function
         * @param builder specifies a function to build a map
         */
        public inline fun build(builder: ParametersBuilder.() -> Unit): Parameters = ParametersBuilder().apply(builder).build()
    }

}

@Suppress("KDocMissingDocumentation")
public class ParametersBuilder(size: Int = 8) : StringValuesBuilder(true, size) {
    override fun build(): Parameters {
        require(!built) { "ParametersBuilder can only build a single Parameters instance" }
        built = true
        return ParametersImpl(values)
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Empty parameters is internal",
    replaceWith = ReplaceWith("Parameters.Empty"),
    level = DeprecationLevel.ERROR
)
public object EmptyParameters : Parameters {
    override val caseInsensitiveName: Boolean get() = true
    override fun getAll(name: String): List<String>? = null
    override fun names(): Set<String> = emptySet()
    override fun entries(): Set<Map.Entry<String, List<String>>> = emptySet()
    override fun isEmpty(): Boolean = true
    override fun toString(): String = "Parameters ${entries()}"

    override fun equals(other: Any?): Boolean = other is Parameters && other.isEmpty()
}

/**
 * Returns an empty parameters instance
 */
public fun parametersOf(): Parameters = Parameters.Empty

/**
 * Creates a parameters instance containing only single pair
 */
public fun parametersOf(name: String, value: String): Parameters = ParametersSingleImpl(name, listOf(value))

/**
 * Creates a parameters instance containing only single pair of [name] with multiple [values]
 */
public fun parametersOf(name: String, values: List<String>): Parameters = ParametersSingleImpl(name, values)

/**
 * Creates a parameters instance from the specified [pairs]
 */
public fun parametersOf(vararg pairs: Pair<String, List<String>>): Parameters = ParametersImpl(pairs.asList().toMap())

@Suppress("KDocMissingDocumentation")
@InternalAPI
public class ParametersImpl(values: Map<String, List<String>> = emptyMap()) : Parameters, StringValuesImpl(true, values) {
    override fun toString(): String = "Parameters ${entries()}"
}

@Suppress("KDocMissingDocumentation")
@InternalAPI
public class ParametersSingleImpl(name: String, values: List<String>) : Parameters, StringValuesSingleImpl(true, name, values) {
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
    else -> throw IllegalArgumentException("Cannot concatenate Parameters with case-sensitive and case-insensitive names")
}
