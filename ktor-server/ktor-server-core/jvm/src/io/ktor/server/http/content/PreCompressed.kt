/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.io.*
import java.net.*

/**
 * Supported pre compressed file types and associated extensions
 *
 * **See Also:** [Accept-Encoding](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding)
 */
public enum class CompressedFileType(public val extension: String, public val encoding: String = extension) {
    BROTLI("br"),
    GZIP("gz", "gzip");

    @Deprecated("This will become internal")
    public fun file(plain: File): File = File("${plain.absolutePath}.$extension")
}

internal val compressedKey = AttributeKey<List<CompressedFileType>>("StaticContentCompressed")

internal val RoutingBuilder.staticContentEncodedTypes: List<CompressedFileType>?
    get() = attributes.getOrNull(compressedKey) ?: parent?.staticContentEncodedTypes

internal class PreCompressedResponse(
    private val original: ReadChannelContent,
    private val encoding: String?,
) : OutgoingContent.ReadChannelContent() {
    override val contentLength get() = original.contentLength
    override val contentType get() = original.contentType
    override val status get() = original.status
    override fun readFrom() = original.readFrom()
    override fun readFrom(range: LongRange) = original.readFrom(range)
    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        if (encoding == null) return@lazy original.headers

        Headers.build {
            appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, encoding)
        }
    }

    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
}

internal fun bestCompressionFit(
    file: File,
    acceptEncoding: List<HeaderValue>,
    compressedTypes: List<CompressedFileType>?
): CompressedFileType? {
    val acceptedEncodings = acceptEncoding.map { it.value }.toSet()
    // We respect the order in compressedTypes, not the one on Accept header
    @Suppress("DEPRECATION")
    return compressedTypes
        ?.filter { it.encoding in acceptedEncodings }
        ?.firstOrNull { it.file(file).isFile }
}

internal class CompressedResource(
    val url: URL,
    val content: OutgoingContent.ReadChannelContent,
    val compression: CompressedFileType
)

internal fun bestCompressionFit(
    call: BaseCall,
    resource: String,
    packageName: String?,
    acceptEncoding: List<HeaderValue>,
    compressedTypes: List<CompressedFileType>?,
    contentType: (URL) -> ContentType
): CompressedResource? {
    val acceptedEncodings = acceptEncoding.map { it.value }.toSet()
    // We respect the order in compressedTypes, not the one on Accept header
    return compressedTypes
        ?.asSequence()
        ?.filter { it.encoding in acceptedEncodings }
        ?.mapNotNull {
            val compressed = "$resource.${it.extension}"
            val resolved = call.application.resolveResource(compressed, packageName) { url ->
                val requestPath = url.path.replace(
                    Regex("${Regex.escapeReplacement(compressed.substringAfterLast(File.separator))}$"),
                    resource.substringAfterLast(File.separator)
                )
                contentType(URL(url.protocol, url.host, url.port, requestPath))
            } ?: return@mapNotNull null
            CompressedResource(resolved.first, resolved.second, it)
        }
        ?.firstOrNull()
}

internal suspend fun BaseCall.respondStaticFile(
    requestedFile: File,
    compressedTypes: List<CompressedFileType>?,
    contentType: (File) -> ContentType = { ContentType.defaultForFile(it) },
    cacheControl: (File) -> List<CacheControl> = { emptyList() },
    modify: suspend (File, BaseCall) -> Unit = { _, _ -> }
) {
    val bestCompressionFit = bestCompressionFit(requestedFile, request.acceptEncodingItems(), compressedTypes)
    val cacheControlValues = cacheControl(requestedFile).joinToString(", ")
    if (bestCompressionFit == null) {
        if (requestedFile.isFile) {
            if (cacheControlValues.isNotEmpty()) response.header(HttpHeaders.CacheControl, cacheControlValues)
            modify(requestedFile, this)
            respond(LocalFileContent(requestedFile, contentType(requestedFile)))
        }
        return
    }
    attributes.put(SuppressionAttribute, true)
    @Suppress("DEPRECATION")
    val compressedFile = bestCompressionFit.file(requestedFile)
    if (compressedFile.isFile) {
        if (cacheControlValues.isNotEmpty()) response.header(HttpHeaders.CacheControl, cacheControlValues)
        modify(requestedFile, this)
        val localFileContent = LocalFileContent(compressedFile, contentType(requestedFile))
        respond(PreCompressedResponse(localFileContent, bestCompressionFit.encoding))
    }
}

internal suspend fun BaseCall.respondStaticResource(
    requestedResource: String,
    packageName: String?,
    compressedTypes: List<CompressedFileType>?,
    contentType: (URL) -> ContentType = { ContentType.defaultForFileExtension(it.path.extension()) },
    cacheControl: (URL) -> List<CacheControl> = { emptyList() },
    modifier: suspend (URL, BaseCall) -> Unit = { _, _ -> },
    exclude: (URL) -> Boolean = { false }
) {
    val bestCompressionFit = bestCompressionFit(
        call = this,
        resource = requestedResource,
        packageName = packageName,
        acceptEncoding = request.acceptEncodingItems(),
        compressedTypes = compressedTypes,
        contentType = contentType
    )

    if (bestCompressionFit != null) {
        if (exclude(bestCompressionFit.url)) {
            respond(HttpStatusCode.Forbidden)
            return
        }
        attributes.put(SuppressionAttribute, true)
        val cacheControlValues = cacheControl(bestCompressionFit.url).joinToString(", ")
        if (cacheControlValues.isNotEmpty()) response.header(HttpHeaders.CacheControl, cacheControlValues)
        modifier(bestCompressionFit.url, this)
        respond(PreCompressedResponse(bestCompressionFit.content, bestCompressionFit.compression.encoding))
        return
    }

    val content = application.resolveResource(
        path = requestedResource,
        resourcePackage = packageName,
        mimeResolve = contentType
    )
    if (content != null) {
        if (exclude(content.first)) {
            respond(HttpStatusCode.Forbidden)
            return
        }
        val cacheControlValues = cacheControl(content.first).joinToString(", ")
        if (cacheControlValues.isNotEmpty()) response.header(HttpHeaders.CacheControl, cacheControlValues)
        modifier(content.first, this)
        respond(content.second)
    }
}
