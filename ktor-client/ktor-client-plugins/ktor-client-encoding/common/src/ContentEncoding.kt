/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.compression

import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.compression.ContentEncoding")

/**
 * A configuration for the [ContentEncoding] plugin.
 */
@KtorDsl
public class ContentEncodingConfig {

    public enum class Mode(internal val request: Boolean, internal val response: Boolean) {
        CompressRequest(true, false), DecompressResponse(false, true), All(true, true)
    }

    internal val encoders: MutableMap<String, ContentEncoder> = CaseInsensitiveMap()

    internal val qualityValues: MutableMap<String, Float> = CaseInsensitiveMap()

    public var mode: Mode = Mode.DecompressResponse

    /**
     * Installs the `gzip` encoder.
     *
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun gzip(quality: Float? = null) {
        customEncoder(GZipEncoder, quality)
    }

    /**
     * Installs the `deflate` encoder.
     *
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun deflate(quality: Float? = null) {
        customEncoder(DeflateEncoder, quality)
    }

    /**
     * Installs the `identity` encoder.
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun identity(quality: Float? = null) {
        customEncoder(IdentityEncoder, quality)
    }

    /**
     * Installs a custom encoder.
     *
     * @param encoder a custom encoder to use.
     * @param quality a priority value to use in the `Accept-Encoding` header.
     */
    public fun customEncoder(encoder: ContentEncoder, quality: Float? = null) {
        val name = encoder.name
        encoders[name.lowercase()] = encoder

        if (quality == null) {
            qualityValues.remove(name)
        } else {
            qualityValues[name] = quality
        }
    }
}

/**
 * A plugin that allows you to enable specified compression algorithms (such as `gzip` and `deflate`) and configure their settings.
 * This plugin serves two primary purposes:
 * - Sets the `Accept-Encoding` header with the specified quality value.
 * - Decodes content received from a server to obtain the original payload.
 *
 * You can learn more from [Content encoding](https://ktor.io/docs/content-encoding.html).
 */
@OptIn(InternalAPI::class)
public val ContentEncoding: ClientPlugin<ContentEncodingConfig> =
    createClientPlugin("HttpEncoding", ::ContentEncodingConfig) {
        val encoders: Map<String, ContentEncoder> = pluginConfig.encoders
        val qualityValues: Map<String, Float> = pluginConfig.qualityValues
        val mode = pluginConfig.mode

        val requestHeader = buildString {
            for (encoder in encoders.values) {
                if (isNotEmpty()) append(',')

                append(encoder.name)

                val quality = qualityValues[encoder.name] ?: continue
                check(quality in 0.0..1.0) { "Invalid quality value: $quality for encoder: $encoder" }

                val qualityValue = quality.toString().take(5)
                append(";q=$qualityValue")
            }
        }

        fun CoroutineScope.decode(response: HttpResponse): HttpResponse {
            val encodings = response.headers[HttpHeaders.ContentEncoding]?.split(",")?.map { it.trim().lowercase() }
                ?: run {
                    LOGGER.trace(
                        "Empty or no Content-Encoding header in response. " +
                            "Skipping ContentEncoding for ${response.call.request.url}"
                    )
                    return response
                }

            var current = response.rawContent
            for (encoding in encodings.reversed()) {
                val encoder: Encoder = encoders[encoding] ?: throw UnsupportedContentEncodingException(encoding)

                LOGGER.trace("Decoding response with $encoder for ${response.call.request.url}")
                with(encoder) {
                    current = decode(current, response.coroutineContext)
                }
            }

            val headers = headers {
                response.headers.forEach { name, values ->
                    if (name.equals(HttpHeaders.ContentEncoding, ignoreCase = true)) return@forEach
                    appendAll(name, values)
                }
                val remainingEncodings = encodings.filter { !encodings.contains(it) }
                if (remainingEncodings.isNotEmpty()) {
                    append(HttpHeaders.ContentEncoding, remainingEncodings.joinToString(","))
                }
            }
            response.call.attributes.put(DecompressionListAttribute, encodings)
            return response.call.wrap(current, headers).response
        }

        onRequest { request, _ ->
            if (!mode.response) return@onRequest
            if (request.headers.contains(HttpHeaders.AcceptEncoding)) return@onRequest
            LOGGER.trace("Adding Accept-Encoding=$requestHeader for ${request.url}")
            request.headers[HttpHeaders.AcceptEncoding] = requestHeader
        }

        on(AfterRenderHook) { request, content ->
            if (!mode.request) return@on null

            val encoderNames = request.attributes.getOrNull(CompressionListAttribute) ?: run {
                LOGGER.trace("Skipping request compression for ${request.url} because no compressions set")
                return@on null
            }

            LOGGER.trace("Compressing request body for ${request.url} using $encoderNames")
            val selectedEncoders = encoderNames.map {
                encoders[it] ?: throw UnsupportedContentEncodingException(it)
            }

            if (selectedEncoders.isEmpty()) return@on null
            selectedEncoders.fold(content) { compressed, encoder ->
                compressed.compressed(encoder, request.executionContext) ?: compressed
            }
        }

        on(ReceiveStateHook) { response ->
            if (!mode.response) return@on null

            val method = response.request.method
            val contentLength = response.contentLength()

            if (contentLength == 0L) return@on null
            if (contentLength == null && method == HttpMethod.Head) return@on null

            return@on response.call.decode(response)
        }
    }

internal object AfterRenderHook : ClientHook<suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent?> {
    val afterRenderPhase = PipelinePhase("AfterRender")
    override fun install(
        client: HttpClient,
        handler: suspend (HttpRequestBuilder, OutgoingContent) -> OutgoingContent?
    ) {
        client.requestPipeline.insertPhaseAfter(HttpRequestPipeline.Render, afterRenderPhase)
        client.requestPipeline.intercept(afterRenderPhase) {
            val result = handler(context, subject as OutgoingContent)
            if (result != null) proceedWith(result)
        }
    }
}

internal object ReceiveStateHook : ClientHook<suspend (HttpResponse) -> HttpResponse?> {

    override fun install(
        client: HttpClient,
        handler: suspend (HttpResponse) -> HttpResponse?
    ) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) {
            val result = handler(it)
            if (result != null) proceedWith(result)
        }
    }
}

/**
 * Installs or configures the [ContentEncoding] plugin.
 *
 * @param block: a [ContentEncoding] configuration.
 */
public fun HttpClientConfig<*>.ContentEncoding(
    mode: ContentEncodingConfig.Mode = ContentEncodingConfig.Mode.DecompressResponse,
    block: ContentEncodingConfig.() -> Unit = {
        gzip()
        deflate()
        identity()
    }
) {
    install(ContentEncoding) {
        this.mode = mode
        block()
    }
}

public class UnsupportedContentEncodingException(encoding: String) :
    IllegalStateException("Content-Encoding: $encoding unsupported.")

internal val CompressionListAttribute: AttributeKey<List<String>> = AttributeKey("CompressionListAttribute")
internal val DecompressionListAttribute: AttributeKey<List<String>> = AttributeKey("DecompressionListAttribute")

/**
 * Compresses request body using [ContentEncoding] plugin.
 *
 * @param contentEncoderName names of compression encoders to use, such as "gzip", "deflate", etc
 */
public fun HttpRequestBuilder.compress(vararg contentEncoderName: String) {
    attributes.put(CompressionListAttribute, contentEncoderName.toList())
}

/**
 * Compress request body using [ContentEncoding] plugin.
 *
 * @param contentEncoderNames names of compression encoders to use, such as "gzip", "deflate", etc
 */
public fun HttpRequestBuilder.compress(contentEncoderNames: List<String>) {
    attributes.put(CompressionListAttribute, contentEncoderNames)
}

/**
 * List of [ContentEncoder] names that were used to decode response body.
 */
public val HttpResponse.appliedDecoders: List<String>
    get() = call.attributes.getOrNull(DecompressionListAttribute) ?: emptyList()
