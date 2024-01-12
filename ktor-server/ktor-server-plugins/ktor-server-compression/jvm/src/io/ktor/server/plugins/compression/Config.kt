/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.compression

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * A configuration for the [Compression] plugin.
 */
public data class CompressionOptions(
    /**
     * Provides access to a map of encoders.
     */
    val encoders: Map<String, CompressionEncoderConfig> = emptyMap(),
    /**
     * Conditions for all encoders.
     */
    val conditions: List<ApplicationCall.(OutgoingContent) -> Boolean> = emptyList()
)

/**
 * An encoder configuration for the [Compression] plugin.
 */
public data class CompressionEncoderConfig(
    /**
     * An encoder implementation.
     */
    val encoder: ContentEncoder,
    /**
     * Conditions for an encoder.
     */
    val conditions: List<ApplicationCall.(OutgoingContent) -> Boolean>,
    /**
     * A priority of an encoder.
     */
    val priority: Double
)

/**
 * A configuration for the [Compression] plugin.
 */
@KtorDsl
public class CompressionConfig : ConditionsHolderBuilder {

    public enum class Mode(internal val request: Boolean, internal val response: Boolean) {
        CompressResponse(false, true), DecompressRequest(true, false), All(true, true)
    }

    /**
     * Specifies if the plugin should compress response, decompress request, or both.
     */
    public var mode: Mode = Mode.All

    /**
     * Provides access to a map of encoders.
     */
    public val encoders: MutableMap<String, CompressionEncoderBuilder> = hashMapOf()

    override val conditions: MutableList<ApplicationCall.(OutgoingContent) -> Boolean> = arrayListOf()

    /**
     * Appends an [encoder] with the [block] configuration.
     */
    public fun encoder(
        encoder: ContentEncoder,
        block: CompressionEncoderBuilder.() -> Unit = {}
    ) {
        if (encoder.name in encoders) {
            throw IllegalArgumentException("Encoder ${encoder.name} is already registered")
        }

        encoders[encoder.name] = CompressionEncoderBuilder(encoder).apply(block)
    }

    /**
     * Appends the default configuration with the `gzip`, `deflate`, and `identity` encoders.
     */
    public fun default() {
        gzip()
        deflate()
        identity()
    }

    /**
     * Builds [CompressionOptions].
     */
    internal fun buildOptions(): CompressionOptions = CompressionOptions(
        encoders = encoders.mapValues { (_, builder) ->
            if (conditions.none() && builder.conditions.none()) {
                builder.defaultConditions()
            }

            builder.buildConfig()
        },
        conditions = conditions.toList()
    )
}

/**
 * A builder for conditions.
 */
public interface ConditionsHolderBuilder {
    /**
     * Preconditions applied to every response object to check if it should be compressed.
     */
    public val conditions: MutableList<ApplicationCall.(OutgoingContent) -> Boolean>
}

/**
 * A builder for compression encoder configuration.
 * @property name of encoder
 * @property encoder instance
 */
@Suppress("MemberVisibilityCanBePrivate")
public class CompressionEncoderBuilder internal constructor(
    public val encoder: ContentEncoder
) : ConditionsHolderBuilder {
    /**
     * A list of conditions for this encoder
     */
    override val conditions: ArrayList<ApplicationCall.(OutgoingContent) -> Boolean> = arrayListOf()

    /**
     * A priority for this encoder.
     */
    public var priority: Double = 1.0

    internal fun buildConfig(): CompressionEncoderConfig {
        return CompressionEncoderConfig(encoder, conditions.toList(), priority)
    }
}

/**
 * Appends the `gzip` encoder with the [block] configuration.
 */
public fun CompressionConfig.gzip(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder(GZipEncoder, block)
}

/**
 * Appends the `deflate` encoder with the [block] configuration and the 0.9 priority.
 */
public fun CompressionConfig.deflate(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder(DeflateEncoder) {
        priority = 0.9
        block()
    }
}

/**
 * Appends the `identity` encoder with the [block] configuration.
 */
public fun CompressionConfig.identity(block: CompressionEncoderBuilder.() -> Unit = {}) {
    encoder(IdentityEncoder, block)
}

/**
 * Appends a custom condition to the encoder or the [Compression] configuration.
 * A predicate returns `true` when a response need to be compressed.
 * If at least one condition is not met, a response isn't compressed.
 *
 * Note that adding a single condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.condition(predicate: ApplicationCall.(OutgoingContent) -> Boolean) {
    conditions.add(predicate)
}

/**
 * Appends a minimum size condition to the encoder or the [Compression] configuration.
 *
 * Note that adding a single minimum size condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.minimumSize(minSize: Long) {
    condition { content -> content.contentLength?.let { it >= minSize } ?: true }
}

/**
 * Appends a content type condition to the encoder or the [Compression] configuration.
 *
 * Note that adding a single condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.matchContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType = content.contentType ?: return@condition false
        mimeTypes.any { contentType.match(it) }
    }
}

/**
 * Appends a content type exclusion condition to the encoder or the [Compression] configuration.
 *
 * Note that adding a single match condition removes the default configuration.
 */
public fun ConditionsHolderBuilder.excludeContentType(vararg mimeTypes: ContentType) {
    condition { content ->
        val contentType =
            content.contentType ?: response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                ?: return@condition true

        mimeTypes.none { excludePattern -> contentType.match(excludePattern) }
    }
}

/**
 * Configures default compression options.
 */
private fun ConditionsHolderBuilder.defaultConditions() {
    excludeContentType(
        ContentType.Video.Any,
        ContentType.Image.JPEG,
        ContentType.Image.PNG,
        ContentType.Audio.Any,
        ContentType.MultiPart.Any,
        ContentType.Text.EventStream
    )

    minimumSize(DEFAULT_MINIMAL_COMPRESSION_SIZE)
}
