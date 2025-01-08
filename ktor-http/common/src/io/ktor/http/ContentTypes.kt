/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.utils.io.charsets.*

/**
 * Represents a value for a `Content-Type` header.
 * @property contentType represents a type part of the media type.
 * @property contentSubtype represents a subtype part of the media type.
 */
public class ContentType private constructor(
    public val contentType: String,
    public val contentSubtype: String,
    existingContent: String,
    parameters: List<HeaderValueParam> = emptyList()
) : HeaderValueWithParameters(existingContent, parameters) {

    public constructor(
        contentType: String,
        contentSubtype: String,
        parameters: List<HeaderValueParam> = emptyList()
    ) : this(
        contentType,
        contentSubtype,
        "$contentType/$contentSubtype",
        parameters
    )

    /**
     * Creates a copy of `this` type with the added parameter with the [name] and [value].
     */
    public fun withParameter(name: String, value: String): ContentType {
        if (hasParameter(name, value)) return this

        return ContentType(contentType, contentSubtype, content, parameters + HeaderValueParam(name, value))
    }

    private fun hasParameter(name: String, value: String): Boolean = when (parameters.size) {
        0 -> false
        1 -> parameters[0].let { it.name.equals(name, ignoreCase = true) && it.value.equals(value, ignoreCase = true) }
        else -> parameters.any { it.name.equals(name, ignoreCase = true) && it.value.equals(value, ignoreCase = true) }
    }

    /**
     * Creates a copy of `this` type without any parameters
     */
    public fun withoutParameters(): ContentType = when {
        parameters.isEmpty() -> this
        else -> ContentType(contentType, contentSubtype)
    }

    /**
     * Checks if `this` type matches a [pattern] type taking into account placeholder symbols `*` and parameters.
     */
    public fun match(pattern: ContentType): Boolean {
        if (pattern.contentType != "*" && !pattern.contentType.equals(contentType, ignoreCase = true)) {
            return false
        }

        if (pattern.contentSubtype != "*" && !pattern.contentSubtype.equals(contentSubtype, ignoreCase = true)) {
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

    /**
     * Checks if `this` type matches a [pattern] type taking into account placeholder symbols `*` and parameters.
     */
    public fun match(pattern: String): Boolean = match(parse(pattern))

    override fun equals(other: Any?): Boolean =
        other is ContentType &&
            contentType.equals(other.contentType, ignoreCase = true) &&
            contentSubtype.equals(other.contentSubtype, ignoreCase = true) &&
            parameters == other.parameters

    override fun hashCode(): Int {
        var result = contentType.lowercase().hashCode()
        result += 31 * result + contentSubtype.lowercase().hashCode()
        result += 31 * parameters.hashCode()
        return result
    }

    public companion object {
        /**
         * Parses a string representing a `Content-Type` header into a [ContentType] instance.
         */
        public fun parse(value: String): ContentType {
            if (value.isBlank()) return Any

            return parse(value) { parts, parameters ->
                val slash = parts.indexOf('/')

                if (slash == -1) {
                    if (parts.trim() == "*") return Any

                    throw BadContentTypeFormatException(value)
                }

                val type = parts.substring(0, slash).trim()

                if (type.isEmpty()) {
                    throw BadContentTypeFormatException(value)
                }

                val subtype = parts.substring(slash + 1).trim()

                if (type.contains(' ') || subtype.contains(' ')) {
                    throw BadContentTypeFormatException(value)
                }

                if (subtype.isEmpty() || subtype.contains('/')) {
                    throw BadContentTypeFormatException(value)
                }

                ContentType(type, subtype, parameters)
            }
        }

        /**
         * Represents a pattern `* / *` to match any content type.
         */
        public val Any: ContentType = ContentType("*", "*")
    }

    /**
     * Provides a list of standard subtypes of an `application` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Application {
        public const val TYPE: String = "application"

        /**
         * Represents a pattern `application / *` to match any application content type.
         */
        public val Any: ContentType = ContentType(TYPE, "*")
        public val Atom: ContentType = ContentType(TYPE, "atom+xml")
        public val Cbor: ContentType = ContentType(TYPE, "cbor")
        public val Json: ContentType = ContentType(TYPE, "json")
        public val HalJson: ContentType = ContentType(TYPE, "hal+json")
        public val JavaScript: ContentType = ContentType(TYPE, "javascript")
        public val OctetStream: ContentType = ContentType(TYPE, "octet-stream")
        public val Rss: ContentType = ContentType(TYPE, "rss+xml")
        public val Soap: ContentType = ContentType(TYPE, "soap+xml")
        public val Xml: ContentType = ContentType(TYPE, "xml")
        public val Xml_Dtd: ContentType = ContentType(TYPE, "xml-dtd")
        public val Zip: ContentType = ContentType(TYPE, "zip")
        public val GZip: ContentType = ContentType(TYPE, "gzip")
        public val FormUrlEncoded: ContentType = ContentType(TYPE, "x-www-form-urlencoded")
        public val Pdf: ContentType = ContentType(TYPE, "pdf")
        public val Xlsx: ContentType = ContentType(TYPE, "vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        public val Docx: ContentType = ContentType(TYPE, "vnd.openxmlformats-officedocument.wordprocessingml.document")
        public val Pptx: ContentType =
            ContentType(TYPE, "vnd.openxmlformats-officedocument.presentationml.presentation")
        public val ProtoBuf: ContentType = ContentType(TYPE, "protobuf")
        public val Wasm: ContentType = ContentType(TYPE, "wasm")
        public val ProblemJson: ContentType = ContentType(TYPE, "problem+json")
        public val ProblemXml: ContentType = ContentType(TYPE, "problem+xml")

        /** Checks that the given [contentType] has type `application/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `application/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of an `audio` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Audio {
        public const val TYPE: String = "audio"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val MP4: ContentType = ContentType(TYPE, "mp4")
        public val MPEG: ContentType = ContentType(TYPE, "mpeg")
        public val OGG: ContentType = ContentType(TYPE, "ogg")

        /** Checks that the given [contentType] has type `audio/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `audio/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of an `image` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Image {
        public const val TYPE: String = "image"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val GIF: ContentType = ContentType(TYPE, "gif")
        public val JPEG: ContentType = ContentType(TYPE, "jpeg")
        public val PNG: ContentType = ContentType(TYPE, "png")
        public val SVG: ContentType = ContentType(TYPE, "svg+xml")
        public val XIcon: ContentType = ContentType(TYPE, "x-icon")

        /** Checks that the given [contentType] has type `image/`. */
        public operator fun contains(contentSubtype: String): Boolean =
            contentSubtype.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `image/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `message` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Message {
        public const val TYPE: String = "message"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Http: ContentType = ContentType(TYPE, "http")

        /** Checks that the given [contentType] has type `message/`. */
        public operator fun contains(contentSubtype: String): Boolean =
            contentSubtype.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `message/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `multipart` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object MultiPart {
        public const val TYPE: String = "multipart"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Mixed: ContentType = ContentType(TYPE, "mixed")
        public val Alternative: ContentType = ContentType(TYPE, "alternative")
        public val Related: ContentType = ContentType(TYPE, "related")
        public val FormData: ContentType = ContentType(TYPE, "form-data")
        public val Signed: ContentType = ContentType(TYPE, "signed")
        public val Encrypted: ContentType = ContentType(TYPE, "encrypted")
        public val ByteRanges: ContentType = ContentType(TYPE, "byteranges")

        /** Checks that the given [contentType] has type `multipart/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `multipart/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `text` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Text {
        public const val TYPE: String = "text"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Plain: ContentType = ContentType(TYPE, "plain")
        public val CSS: ContentType = ContentType(TYPE, "css")
        public val CSV: ContentType = ContentType(TYPE, "csv")
        public val Html: ContentType = ContentType(TYPE, "html")
        public val JavaScript: ContentType = ContentType(TYPE, "javascript")
        public val VCard: ContentType = ContentType(TYPE, "vcard")
        public val Xml: ContentType = ContentType(TYPE, "xml")
        public val EventStream: ContentType = ContentType(TYPE, "event-stream")

        /** Checks that the given [contentType] has type `text/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `text/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `video` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Video {
        public const val TYPE: String = "video"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val MPEG: ContentType = ContentType(TYPE, "mpeg")
        public val MP4: ContentType = ContentType(TYPE, "mp4")
        public val OGG: ContentType = ContentType(TYPE, "ogg")
        public val QuickTime: ContentType = ContentType(TYPE, "quicktime")

        /** Checks that the given [contentType] has type `video/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `video/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `font` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Font {
        public const val TYPE: String = "font"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Collection: ContentType = ContentType(TYPE, "collection")
        public val Otf: ContentType = ContentType(TYPE, "otf")
        public val Sfnt: ContentType = ContentType(TYPE, "sfnt")
        public val Ttf: ContentType = ContentType(TYPE, "ttf")
        public val Woff: ContentType = ContentType(TYPE, "woff")
        public val Woff2: ContentType = ContentType(TYPE, "woff2")

        /** Checks that the given [contentType] has type `font/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `font/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }
}

/**
 * Exception thrown when a content type string is malformed.
 */
public class BadContentTypeFormatException(value: String) : Exception("Bad Content-Type format: $value")

/**
 * Creates a copy of `this` type with the added charset parameter with [charset] value.
 */
public fun ContentType.withCharset(charset: Charset): ContentType =
    withParameter("charset", charset.name)

/**
 * Creates a copy of `this` type with the added charset parameter with [charset] value
 * if [ContentType] is not ignored
 */
public fun ContentType.withCharsetIfNeeded(charset: Charset): ContentType =
    if (contentType.lowercase() != "text") {
        this
    } else {
        withParameter("charset", charset.name)
    }

/**
 * Extracts a [Charset] value from the given `Content-Type`, `Content-Disposition` or similar header value.
 */
public fun HeaderValueWithParameters.charset(): Charset? = parameter("charset")?.let {
    try {
        Charsets.forName(it)
    } catch (exception: IllegalArgumentException) {
        null
    }
}
