/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        /**
         * Represents a pattern `application / *` to match any application content type.
         */
        public val Any: ContentType = ContentType("application", "*")
        public val Atom: ContentType = ContentType("application", "atom+xml")
        public val Cbor: ContentType = ContentType("application", "cbor")
        public val Json: ContentType = ContentType("application", "json")
        public val HalJson: ContentType = ContentType("application", "hal+json")
        public val JavaScript: ContentType = ContentType("application", "javascript")
        public val OctetStream: ContentType = ContentType("application", "octet-stream")
        public val Rss: ContentType = ContentType("application", "rss+xml")
        public val Soap: ContentType = ContentType("application", "soap+xml")
        public val Xml: ContentType = ContentType("application", "xml")
        public val Xml_Dtd: ContentType = ContentType("application", "xml-dtd")
        public val Zip: ContentType = ContentType("application", "zip")
        public val GZip: ContentType = ContentType("application", "gzip")

        public val FormUrlEncoded: ContentType =
            ContentType("application", "x-www-form-urlencoded")

        public val Pdf: ContentType = ContentType("application", "pdf")
        public val Xlsx: ContentType = ContentType(
            "application",
            "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
        public val Docx: ContentType = ContentType(
            "application",
            "vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        public val Pptx: ContentType = ContentType(
            "application",
            "vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        public val ProtoBuf: ContentType = ContentType("application", "protobuf")
        public val Wasm: ContentType = ContentType("application", "wasm")
        public val ProblemJson: ContentType = ContentType("application", "problem+json")
        public val ProblemXml: ContentType = ContentType("application", "problem+xml")
    }

    /**
     * Provides a list of standard subtypes of an `audio` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Audio {
        public val Any: ContentType = ContentType("audio", "*")
        public val MP4: ContentType = ContentType("audio", "mp4")
        public val MPEG: ContentType = ContentType("audio", "mpeg")
        public val OGG: ContentType = ContentType("audio", "ogg")
    }

    /**
     * Provides a list of standard subtypes of an `image` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Image {
        public val Any: ContentType = ContentType("image", "*")
        public val GIF: ContentType = ContentType("image", "gif")
        public val JPEG: ContentType = ContentType("image", "jpeg")
        public val PNG: ContentType = ContentType("image", "png")
        public val SVG: ContentType = ContentType("image", "svg+xml")
        public val XIcon: ContentType = ContentType("image", "x-icon")
    }

    /**
     * Provides a list of standard subtypes of a `message` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Message {
        public val Any: ContentType = ContentType("message", "*")
        public val Http: ContentType = ContentType("message", "http")
    }

    /**
     * Provides a list of standard subtypes of a `multipart` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object MultiPart {
        public val Any: ContentType = ContentType("multipart", "*")
        public val Mixed: ContentType = ContentType("multipart", "mixed")
        public val Alternative: ContentType = ContentType("multipart", "alternative")
        public val Related: ContentType = ContentType("multipart", "related")
        public val FormData: ContentType = ContentType("multipart", "form-data")
        public val Signed: ContentType = ContentType("multipart", "signed")
        public val Encrypted: ContentType = ContentType("multipart", "encrypted")
        public val ByteRanges: ContentType = ContentType("multipart", "byteranges")
    }

    /**
     * Provides a list of standard subtypes of a `text` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Text {
        public val Any: ContentType = ContentType("text", "*")
        public val Plain: ContentType = ContentType("text", "plain")
        public val CSS: ContentType = ContentType("text", "css")
        public val CSV: ContentType = ContentType("text", "csv")
        public val Html: ContentType = ContentType("text", "html")
        public val JavaScript: ContentType = ContentType("text", "javascript")
        public val VCard: ContentType = ContentType("text", "vcard")
        public val Xml: ContentType = ContentType("text", "xml")
        public val EventStream: ContentType = ContentType("text", "event-stream")
    }

    /**
     * Provides a list of standard subtypes of a `video` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Video {
        public val Any: ContentType = ContentType("video", "*")
        public val MPEG: ContentType = ContentType("video", "mpeg")
        public val MP4: ContentType = ContentType("video", "mp4")
        public val OGG: ContentType = ContentType("video", "ogg")
        public val QuickTime: ContentType = ContentType("video", "quicktime")
    }

    /**
     * Provides a list of standard subtypes of a `font` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Font {
        public val Any: ContentType = ContentType("font", "*")
        public val Collection: ContentType = ContentType("font", "collection")
        public val Otf: ContentType = ContentType("font", "otf")
        public val Sfnt: ContentType = ContentType("font", "sfnt")
        public val Ttf: ContentType = ContentType("font", "ttf")
        public val Woff: ContentType = ContentType("font", "woff")
        public val Woff2: ContentType = ContentType("font", "woff2")
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
