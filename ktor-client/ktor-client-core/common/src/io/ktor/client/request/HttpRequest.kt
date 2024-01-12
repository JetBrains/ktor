/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.date.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.reflect.*

/**
 * A request for [HttpClient], first part of [HttpClientCall].
 */
public interface HttpRequest : HttpMessage, CoroutineScope {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    public val call: HttpClientCall

    override val coroutineContext: CoroutineContext get() = call.coroutineContext

    /**
     * The [HttpMethod] or HTTP VERB used for this request.
     */
    public val method: HttpMethod

    /**
     * The [Url] representing the endpoint and the uri for this request.
     */
    public val url: Url

    /**
     * Typed [Attributes] associated to this call serving as a lightweight container.
     */
    public val attributes: Attributes

    /**
     * An [OutgoingContent] representing the request body
     */
    public val content: OutgoingContent
}

/**
 * Contains parameters used to make an HTTP request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public class HttpRequestBuilder : HttpMessageBuilder {
    /**
     * [URLBuilder] to configure the URL for this request.
     */
    public val url: URLBuilder = URLBuilder()

    /**
     * [HttpMethod] used by this request. [HttpMethod.Get] by default.
     */
    public var method: HttpMethod = HttpMethod.Get

    /**
     * [HeadersBuilder] to configure the headers for this request.
     */
    override val headers: HeadersBuilder = HeadersBuilder()

    /**
     * The [body] for this request. Initially [EmptyContent].
     */
    public var body: Any = EmptyContent
        @InternalAPI public set

    /**
     * The [KType] of [body] for this request. Null for default types that don't need serialization.
     */
    public var bodyType: TypeInfo?
        get() = attributes.getOrNull(BodyTypeAttributeKey)

        @InternalAPI set(value) {
            if (value != null) {
                attributes.put(BodyTypeAttributeKey, value)
            } else {
                attributes.remove(BodyTypeAttributeKey)
            }
        }

    /**
     * A deferred used to control the execution of this request.
     */
    public var executionContext: Job = SupervisorJob()
        internal set

    /**
     * Provides access to attributes specific for this request.
     */
    public val attributes: Attributes = Attributes(concurrent = true)

    /**
     * Executes a [block] that configures the [URLBuilder] associated to this request.
     */
    public fun url(block: URLBuilder.(URLBuilder) -> Unit): Unit = url.block(url)

    /**
     * Creates immutable [HttpRequestData].
     */
    @OptIn(InternalAPI::class)
    public fun build(): HttpRequestData = HttpRequestData(
        url.build(),
        method,
        headers.build(),
        body as? OutgoingContent ?: error("No request transformation found: $body"),
        executionContext,
        attributes
    )

    /**
     * Sets request-specific attributes specified by [block].
     */
    public fun setAttributes(block: Attributes.() -> Unit) {
        attributes.apply(block)
    }

    /**
     * Mutates [this] copying all the data from another [builder] using it as the base.
     */
    @InternalAPI
    public fun takeFromWithExecutionContext(builder: HttpRequestBuilder): HttpRequestBuilder {
        executionContext = builder.executionContext
        return takeFrom(builder)
    }

    /**
     * Mutates [this] by copying all the data but execution context from another [builder] using it as the base.
     */
    @OptIn(InternalAPI::class)
    public fun takeFrom(builder: HttpRequestBuilder): HttpRequestBuilder {
        method = builder.method
        body = builder.body
        bodyType = builder.bodyType
        url.takeFrom(builder.url)
        url.encodedPathSegments = url.encodedPathSegments
        headers.appendAll(builder.headers)
        attributes.putAll(builder.attributes)

        return this
    }

    /**
     * Sets capability configuration.
     */
    public fun <T : Any> setCapability(key: HttpClientEngineCapability<T>, capability: T) {
        val capabilities = attributes.computeIfAbsent(ENGINE_CAPABILITIES_KEY) { mutableMapOf() }
        capabilities[key] = capability
    }

    /**
     * Retrieves capability by the key.
     */
    public fun <T : Any> getCapabilityOrNull(key: HttpClientEngineCapability<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return attributes.getOrNull(ENGINE_CAPABILITIES_KEY)?.get(key) as T?
    }

    public companion object
}

/**
 * Actual data of the [HttpRequest], including [url], [method], [headers], [body] and [executionContext].
 * Built by [HttpRequestBuilder].
 */
public class HttpRequestData @InternalAPI constructor(
    public val url: Url,
    public val method: HttpMethod,
    public val headers: Headers,
    public val body: OutgoingContent,
    public val executionContext: Job,
    public val attributes: Attributes
) {
    /**
     * Retrieve extension by its key.
     */
    public fun <T> getCapabilityOrNull(key: HttpClientEngineCapability<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return attributes.getOrNull(ENGINE_CAPABILITIES_KEY)?.get(key) as T?
    }

    /**
     * All extension keys associated with this request.
     */
    internal val requiredCapabilities: Set<HttpClientEngineCapability<*>> =
        attributes.getOrNull(ENGINE_CAPABILITIES_KEY)?.keys ?: emptySet()

    override fun toString(): String = "HttpRequestData(url=$url, method=$method)"
}

/**
 * Data prepared for [HttpResponse].
 */
public class HttpResponseData constructor(
    public val statusCode: HttpStatusCode,
    public val requestTime: GMTDate,
    public val headers: Headers,
    public val version: HttpProtocolVersion,
    public val body: Any,
    public val callContext: CoroutineContext
) {
    public val responseTime: GMTDate = GMTDate()

    override fun toString(): String = "HttpResponseData=(statusCode=$statusCode)"
}

/**
 * Executes a [block] that configures the [HeadersBuilder] associated to this request.
 */
public fun HttpMessageBuilder.headers(block: HeadersBuilder.() -> Unit): HeadersBuilder = headers.apply(block)

/**
 * Mutates [this] copying all the data from another [request] using it as base.
 */
@OptIn(InternalAPI::class)
public fun HttpRequestBuilder.takeFrom(request: HttpRequest): HttpRequestBuilder {
    method = request.method
    body = request.content
    bodyType = attributes.getOrNull(BodyTypeAttributeKey)
    url.takeFrom(request.url)
    headers.appendAll(request.headers)
    attributes.putAll(request.attributes)
    return this
}

/**
 * Executes a [block] that configures the [URLBuilder] associated to this request.
 */
public fun HttpRequestBuilder.url(block: URLBuilder.() -> Unit): Unit = block(url)

/**
 * Sets the [HttpRequestBuilder] from [request].
 */
@OptIn(InternalAPI::class)
public fun HttpRequestBuilder.takeFrom(request: HttpRequestData): HttpRequestBuilder {
    method = request.method
    body = request.body
    bodyType = attributes.getOrNull(BodyTypeAttributeKey)
    url.takeFrom(request.url)
    headers.appendAll(request.headers)
    attributes.putAll(request.attributes)

    return this
}

/**
 * Executes a [block] that configures the [URLBuilder] associated to this request.
 */
public operator fun HttpRequestBuilder.Companion.invoke(block: URLBuilder.() -> Unit): HttpRequestBuilder =
    HttpRequestBuilder().apply { url(block) }

/**
 * Sets the [url] using the specified [scheme], [host], [port] and [path].
 * Pass `null` to keep the existing value in the [URLBuilder].
 */
public fun HttpRequestBuilder.url(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: URLBuilder.() -> Unit = {}
) {
    url.set(scheme, host, port, path, block)
}

/**
 * Constructs a [HttpRequestBuilder] from URL information: [scheme], [host], [port] and [path]
 * and optionally further configures it using [block].
 * Pass `null` to keep the existing value in the [URLBuilder].
 */
public operator fun HttpRequestBuilder.Companion.invoke(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: URLBuilder.() -> Unit = {}
): HttpRequestBuilder = HttpRequestBuilder().apply { url(scheme, host, port, path, block) }

/**
 * Sets the [HttpRequestBuilder.url] from [urlString].
 */
public fun HttpRequestBuilder.url(urlString: String) { // ktlint-disable filename
    url.takeFrom(urlString)
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
public fun HttpRequestData.isUpgradeRequest(): Boolean {
    return body is ClientUpgradeContent
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
public fun HttpRequestData.isSseRequest(): Boolean {
    return body is SSEClientContent
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
public fun needToProcessSSE(data: HttpRequestData, status: HttpStatusCode, headers: Headers): Boolean {
    val contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
    return data.isSseRequest() &&
        status == HttpStatusCode.OK &&
        contentType?.withoutParameters() == ContentType.Text.EventStream
}
