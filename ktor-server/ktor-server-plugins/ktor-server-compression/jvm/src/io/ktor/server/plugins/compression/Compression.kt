/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.compression

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.compression.Compression")

/**
 * The default minimal content size to compress.
 */
internal const val DEFAULT_MINIMAL_COMPRESSION_SIZE: Long = 200L

private object ContentEncoding : Hook<suspend ContentEncoding.Context.(PipelineCall) -> Unit> {

    class Context(private val pipelineContext: PipelineContext<Any, PipelineCall>) {
        fun transformBody(block: (OutgoingContent) -> OutgoingContent?) {
            val transformedContent = block(pipelineContext.subject as OutgoingContent)
            if (transformedContent != null) {
                pipelineContext.subject = transformedContent
            }
        }
    }

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend Context.(PipelineCall) -> Unit
    ) {
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.ContentEncoding) {
            handler(Context(this), call)
        }
    }
}

/**
 * A plugin that provides the capability to compress a response and decompress request bodies.
 * You can use different compression algorithms, including `gzip` and `deflate`,
 * specify the required conditions for compressing data (such as a content type or response size),
 * or even compress data based on specific request parameters.
 *
 * Note that if the request body was decompressed,
 * the plugin will remove [HttpHeaders.ContentEncoding] and [HttpHeaders.ContentLength] headers.
 * Also, it will add [HttpHeaders.TransferEncoding] header with `chunked` value.
 * Original encodings can be accessed through [ApplicationRequest.appliedDecoders] property.
 *
 * The example below shows how to compress JavaScript content using `gzip` with the specified priority:
 * ```kotlin
 * install(Compression) {
 *     gzip {
 *         priority = 0.9
 *         matchContentType(ContentType.Application.JavaScript)
 *     }
 * }
 * ```
 *
 * You can learn more from [Compression](https://ktor.io/docs/compression.html).
 */
public val Compression: RouteScopedPlugin<CompressionConfig> = createRouteScopedPlugin(
    "Compression",
    ::CompressionConfig
) {
    if (pluginConfig.encoders.none()) {
        pluginConfig.default()
    }
    val options = pluginConfig.buildOptions()
    val mode = pluginConfig.mode

    on(ContentEncoding) { call ->
        if (!mode.response) return@on
        encode(call, options)
    }
    onCall { call ->
        if (!mode.request) return@onCall
        decode(call, options)
    }
}

@OptIn(InternalAPI::class)
private fun decode(call: PipelineCall, options: CompressionOptions) {
    val encodingRaw = call.request.headers[HttpHeaders.ContentEncoding]
    if (encodingRaw == null) {
        LOGGER.trace("Skip decompression for ${call.request.uri} because no content encoding provided.")
        return
    }
    val encoding = parseHeaderValue(encodingRaw)
    val encoders = encoding.mapNotNull { options.encoders[it.value] }
    if (encoders.isEmpty()) {
        LOGGER.trace("Skip decompression for ${call.request.uri} because no suitable encoders found.")
        return
    }
    val encoderNames = encoders.map { it.encoder.name }
    if (encoding.size > encoders.size) {
        val missingEncoders = encoding.map { it.value } - encoderNames.toSet()
        call.request.setHeader(HttpHeaders.ContentEncoding, missingEncoders)
        LOGGER.trace(
            "Skip some of decompression for ${call.request.uri} " +
                "because no suitable encoders found for $missingEncoders"
        )
    } else {
        call.request.setHeader(HttpHeaders.ContentEncoding, null)
    }
    val originalChannel = call.request.receiveChannel()
    val decoded = encoders.fold(originalChannel) { content, encoder -> encoder.encoder.decode(content) }
    call.request.setReceiveChannel(decoded)
    call.attributes.put(DecompressionListAttribute, encoderNames)
}

private fun ContentEncoding.Context.encode(call: PipelineCall, options: CompressionOptions) {
    if (call.response.isSSEResponse()) {
        LOGGER.trace("Skip compression for sse response ${call.request.uri} ")
        return
    }

    val comparator = compareBy<Pair<CompressionEncoderConfig, HeaderValue>>(
        { it.second.quality },
        { it.first.priority }
    ).reversed()

    val acceptEncodingRaw = call.request.acceptEncoding()
    if (acceptEncodingRaw == null) {
        LOGGER.trace("Skip compression for ${call.request.uri} because no accept encoding provided.")
        return
    }

    if (call.isCompressionSuppressed) {
        LOGGER.trace("Skip compression for ${call.request.uri} because it is suppressed.")
        return
    }

    val encoders = parseHeaderValue(acceptEncodingRaw)
        .filter { it.value == "*" || it.value in options.encoders }
        .flatMap { header ->
            when (header.value) {
                "*" -> options.encoders.values.map { it to header }
                else -> options.encoders[header.value]?.let { listOf(it to header) } ?: emptyList()
            }
        }
        .sortedWith(comparator)
        .map { it.first }

    if (encoders.isEmpty()) {
        LOGGER.trace("Skip compression for ${call.request.uri} because no encoders provided.")
        return
    }

    transformBody { message ->
        if (options.conditions.any { !it(call, message) }) {
            LOGGER.trace("Skip compression for ${call.request.uri} because preconditions doesn't meet.")
            return@transformBody null
        }

        val encodingHeader = message.headers[HttpHeaders.ContentEncoding]
        if (encodingHeader != null) {
            LOGGER.trace("Skip compression for ${call.request.uri} because content is already encoded.")
            return@transformBody null
        }

        val encoderOptions = encoders.firstOrNull { encoder -> encoder.conditions.all { it(call, message) } }

        if (encoderOptions == null) {
            LOGGER.trace("Skip compression for ${call.request.uri} because no suitable encoder found.")
            return@transformBody null
        }

        LOGGER.trace("Encoding body for ${call.request.uri} using ${encoderOptions.encoder.name}.")
        return@transformBody message.compressed(encoderOptions.encoder)
    }
}

internal val DecompressionListAttribute: AttributeKey<List<String>> = AttributeKey("DecompressionListAttribute")

/**
 * List of [ContentEncoder] names that were used to decode request body.
 */
public val ApplicationRequest.appliedDecoders: List<String>
    get() = call.attributes.getOrNull(DecompressionListAttribute) ?: emptyList()

private fun PipelineResponse.isSSEResponse(): Boolean {
    val contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
    return contentType?.withoutParameters() == ContentType.Text.EventStream
}
