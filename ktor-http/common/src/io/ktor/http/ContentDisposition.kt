/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Represents `Content-Disposition` header value
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.ContentDisposition)
 */
public class ContentDisposition(
    disposition: String,
    parameters: List<HeaderValueParam> = emptyList()
) : HeaderValueWithParameters(disposition, parameters) {
    /**
     * Content disposition value without parameters
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.ContentDisposition.disposition)
     */
    public val disposition: String get() = content

    /**
     * Content disposition name (from parameter named `name`)
     */
    public val name: String?
        get() = parameter(Parameters.Name)

    /**
     * Creates new with parameter appended.
     */
    public fun withParameter(key: String, value: String, encodeValue: Boolean = true): ContentDisposition {
        val encodedValue = if (encodeValue) {
            encodeContentDispositionAttribute(key, value)
        } else {
            value
        }

        return ContentDisposition(disposition, parameters + HeaderValueParam(key, encodedValue))
    }

    /**
     * Creates new with parameters appended
     */
    public fun withParameters(newParameters: List<HeaderValueParam>): ContentDisposition =
        ContentDisposition(disposition, parameters + newParameters)

    override fun equals(other: Any?): Boolean =
        other is ContentDisposition &&
            disposition == other.disposition &&
            parameters == other.parameters

    override fun hashCode(): Int = disposition.hashCode() * 31 + parameters.hashCode()

    @Suppress("unused", "PublicApiImplicitType")
    public companion object {
        /**
         * `Content-Disposition: file`
         */
        public val File: ContentDisposition = ContentDisposition("file")

        /**
         * `Content-Disposition: mixed`
         */
        public val Mixed: ContentDisposition = ContentDisposition("mixed")

        /**
         * `Content-Disposition: attachment`
         */
        public val Attachment: ContentDisposition = ContentDisposition("attachment")

        /**
         * `Content-Disposition: inline`
         */
        public val Inline: ContentDisposition = ContentDisposition("inline")

        /**
         * Parse `Content-Disposition` header [value]
         */
        public fun parse(value: String): ContentDisposition = parse(value) { v, p -> ContentDisposition(v, p) }
    }

    /**
     * Frequently used content disposition parameter names
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    public object Parameters {
        public const val FileName: String = "filename"
        public const val FileNameAsterisk: String = "filename*"
        public const val Name: String = "name"
        public const val CreationDate: String = "creation-date"
        public const val ModificationDate: String = "modification-date"
        public const val ReadDate: String = "read-date"
        public const val Size: String = "size"
        public const val Handling: String = "handling"
    }
}

private fun encodeContentDispositionAttribute(key: String, value: String): String {
    if (key != ContentDisposition.Parameters.FileNameAsterisk) return value
    if (value.startsWith("utf-8''", ignoreCase = true)) return value
    if (value.all { it in ATTRIBUTE_CHARACTERS }) return value

    val encodedValue = value.percentEncode(ATTRIBUTE_CHARACTERS)
    return "utf-8''$encodedValue"
}
