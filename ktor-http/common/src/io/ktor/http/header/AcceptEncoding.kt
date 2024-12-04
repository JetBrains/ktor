/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.header

import io.ktor.http.*

/**
 * Represents the `Accept-Encoding` HTTP header, which specifies the content encoding the client is willing to accept.
 *
 * @property acceptEncoding The encoding type as a string, such as "gzip", "compress", "br", etc.
 * @param parameters Optional list of parameters associated with the encoding, such as quality values (q-values).
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.header.AcceptEncoding)
 */
public class AcceptEncoding(
    public val acceptEncoding: String,
    parameters: List<HeaderValueParam> = emptyList()
) : HeaderValueWithParameters(acceptEncoding, parameters) {

    /**
     * Constructs an `AcceptEncoding` instance with a specified encoding type and q-value.
     *
     * @param acceptEncoding The encoding type, such as "gzip", "compress", "br", etc.
     * @param qValue The quality value (q-value) associated with this encoding.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.header.AcceptEncoding.AcceptEncoding)
     */
    public constructor(acceptEncoding: String, qValue: Double) : this(
        acceptEncoding,
        listOf(HeaderValueParam("q", qValue.toString()))
    )

    /**
     * Companion object containing predefined commonly used `Accept-Encoding` values.
     */
    public companion object {
        public val Gzip: AcceptEncoding = AcceptEncoding("gzip")
        public val Compress: AcceptEncoding = AcceptEncoding("compress")
        public val Deflate: AcceptEncoding = AcceptEncoding("deflate")
        public val Br: AcceptEncoding = AcceptEncoding("br")
        public val Zstd: AcceptEncoding = AcceptEncoding("zstd")
        public val Identity: AcceptEncoding = AcceptEncoding("identity")
        public val All: AcceptEncoding = AcceptEncoding("*")

        /**
         * Merges multiple `AcceptEncoding` instances into a single string separated by commas.
         *
         * @param encodings A variable number of `AcceptEncoding` objects to be merged.
         * @return A string representing the merged `Accept-Encoding` values.
         */
        public fun mergeAcceptEncodings(vararg encodings: AcceptEncoding): String {
            return encodings.joinToString(separator = ", ")
        }
    }

    /**
     * Returns a new `AcceptEncoding` instance with the specified q-value parameter.
     *
     * @param qValue The q-value to be associated with this encoding. The value should be between 0.0 and 1.0.
     * @return A new `AcceptEncoding` instance with the specified q-value, or the same instance if the q-value is already set.
     */
    public fun withQValue(qValue: Double): AcceptEncoding {
        if (qValue.toString() == parameter("q")) {
            return this
        }

        return AcceptEncoding(acceptEncoding, qValue)
    }

    /**
     * Checks if `this` `AcceptEncoding` matches a [pattern] `AcceptEncoding`, taking into account
     * wildcard symbols `*` and parameters such as q-values.
     *
     * @param pattern The `AcceptEncoding` to match against.
     * @return `true` if `this` matches the given [pattern], `false` otherwise.
     */
    public fun match(pattern: AcceptEncoding): Boolean {
        if (pattern.acceptEncoding != "*" && !pattern.acceptEncoding.equals(acceptEncoding, ignoreCase = true)) {
            return false
        }

        for ((patternName, patternValue) in pattern.parameters) {
            val matches = when (patternName) {
                "*" -> {
                    when (patternValue) {
                        "*" -> true
                        else -> parameters.any { p -> p.value.equals(patternValue, ignoreCase = true) }
                    }
                }

                else -> {
                    val value = parameter(patternName)
                    when (patternValue) {
                        "*" -> value != null
                        else -> value.equals(patternValue, ignoreCase = true)
                    }
                }
            }

            if (!matches) {
                return false
            }
        }
        return true
    }

    override fun equals(other: Any?): Boolean = other is AcceptEncoding &&
        acceptEncoding.equals(other.acceptEncoding, ignoreCase = true) &&
        parameters == other.parameters

    override fun hashCode(): Int {
        var hashCode = acceptEncoding.lowercase().hashCode()
        hashCode += 31 * parameters.hashCode()
        return hashCode
    }
}
