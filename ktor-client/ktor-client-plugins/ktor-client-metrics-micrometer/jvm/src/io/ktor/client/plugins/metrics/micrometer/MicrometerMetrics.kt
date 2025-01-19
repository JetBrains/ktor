/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.metrics.micrometer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.binder.http.Outcome
import io.micrometer.core.instrument.logging.*

public const val URI_PATTERN: String = "URI_PATTERN"

private const val TAG_TARGET_SCHEME = "target.scheme"
private const val TAG_TARGET_HOST = "target.host"
private const val TAG_TARGET_PORT = "target.port"
private const val TAG_URI = "uri"
private const val TAG_METHOD = "method"
private const val TAG_STATUS = "status"
private const val TAG_EXCEPTION = "exception"
private const val TAG_VALUE_UNKNOWN = "UNKNOWN"

private val EMPTY_EXCEPTION_TAG: Tag = Tag.of(TAG_EXCEPTION, "null")

private val QUERY_PART_REGEX = "\\?.*$".toRegex()

private val ClientCallTimer = AttributeKey<Timer.Sample>("CallTimer")

/**
 * A configuration for the [MicrometerMetrics] plugin.
 */
@KtorDsl
public class MicrometerMetricsConfig {

    /**
     * Specifies the base name (prefix) of Ktor metrics used for monitoring HTTP client requests.
     * For example, the default "ktor.http.client.requests" values results in the following metrics:
     * - "ktor.http.client.requests.count"
     * - "ktor.http.client.requests.seconds.max"
     *
     * If you change it to "custom.metric.name", the mentioned metrics will look as follows:
     * - "custom.metric.name.count"
     * - "custom.metric.name.seconds.max"
     * @see [MicrometerMetrics]
     */
    public var metricName: String = "ktor.http.client.requests"

    /**
     * Extra tags to add to the metrics
     */
    public var extraTags: Iterable<Tag> = emptyList()

    /**
     * Whether to drop the query part of the URL in the tag or not. Default: `true`
     */
    public var dropQueryPartInUriTag: Boolean = true

    /**
     * Whether to use expanded URL when the pattern is unavailable or not. Default: `true`
     *
     * Note that setting this option to `true` without using URI templates with [HttpRequestBuilder.pathParameters]
     * might lead to cardinality blow up.
     */
    public var useExpandedUrlWhenPatternUnavailable: Boolean = true

    /**
     * Specifies the meter registry for your monitoring system.
     * The example below shows how to create the `PrometheusMeterRegistry`:
     * ```kotlin
     * install(MicrometerMetrics) {
     *     registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
     * }
     * ```
     * @see [MicrometerMetrics]
     */
    public var registry: MeterRegistry = LoggingMeterRegistry()
        set(value) {
            field.close()
            field = value
        }
}

/**
 * A client's plugin that provides the capability to meter HTTP calls with micrometer
 *
 */
public val MicrometerMetrics: ClientPlugin<MicrometerMetricsConfig> =
    createClientPlugin("MicrometerMetrics", ::MicrometerMetricsConfig) {
        val metricName = pluginConfig.metricName
        val extraTags = pluginConfig.extraTags
        val dropQueryPartInUriTag = pluginConfig.dropQueryPartInUriTag
        val useExpandedUrlWhenPatternUnavailable = pluginConfig.useExpandedUrlWhenPatternUnavailable
        val meterRegistry = pluginConfig.registry

        fun Timer.Builder.addDefaultTags(
            targetScheme: String,
            host: String,
            port: String,
            method: String,
            uri: String,
        ) =
            tags(
                Tags.of(
                    Tag.of(TAG_TARGET_SCHEME, targetScheme),
                    Tag.of(TAG_TARGET_HOST, host),
                    Tag.of(TAG_TARGET_PORT, port),
                    Tag.of(TAG_METHOD, method),
                    Tag.of(TAG_URI, uri),
                )
            ).tags(extraTags)

        fun Attributes.uriPattern(host: String, url: Any) =
            getOrNull(UriPatternAttributeKey)
                .let { it ?: url.takeIf { useExpandedUrlWhenPatternUnavailable }?.toString() }
                ?.removeHostPart(host)
                ?.let { it.takeUnless { dropQueryPartInUriTag } ?: it.removeQueryPart() }
                ?: TAG_VALUE_UNKNOWN

        fun Timer.Builder.addDefaultTags(request: HttpRequestBuilder) =
            addDefaultTags(targetScheme = request.url.protocol.name,
                host = request.host,
                port = "${request.port}",
                method = request.method.value,
                uri = request.attributes.uriPattern(request.host, request.url),
            )

        fun Timer.Builder.addDefaultTags(request: HttpRequest) =
            addDefaultTags(targetScheme = request.url.protocol.name,
                host = request.url.host,
                port = "${request.url.port}",
                method = request.method.value,
                uri = request.attributes.uriPattern(request.url.host, request.url),
            )

        on(SendHook) { request ->
            val timer = Timer.start(meterRegistry)
            request.attributes.put(ClientCallTimer, timer)

            try {
                proceed()
            } catch (cause: Throwable) {
                timer.stop(
                    Timer.builder(metricName)
                        .addDefaultTags(request)
                        .tags(
                            Tags.of(
                                Outcome.CLIENT_ERROR.asTag(),
                                Tag.of(TAG_STATUS, TAG_VALUE_UNKNOWN),
                                cause.toTag(),
                            )
                        )
                        .register(meterRegistry)
                )
                throw cause
            }
        }

        on(ReceiveHook) { call ->
            val timer = call.attributes.getOrNull(ClientCallTimer)

            try {
                proceed()
            } catch (cause: Throwable) {
                timer?.stop(
                    Timer.builder(metricName)
                        .addDefaultTags(call.request)
                        .tags(
                            Tags.of(
                                Outcome.CLIENT_ERROR.asTag(),
                                Tag.of(TAG_STATUS, TAG_VALUE_UNKNOWN),
                                cause.toTag(),
                            )
                        )
                        .register(meterRegistry)
                )
                throw cause
            }
        }

        on(ResponseHook) { response ->
            val timer = response.call.attributes.getOrNull(ClientCallTimer)

            try {
                proceed()
                timer?.stop(
                    Timer.builder(metricName)
                        .addDefaultTags(response.request)
                        .tags(
                            Tags.of(
                                Outcome.forStatus(response.status.value).asTag(),
                                Tag.of(TAG_STATUS, "${response.status.value}"),
                                EMPTY_EXCEPTION_TAG,
                            )
                        )
                        .register(meterRegistry)
                )
            } catch (cause: Throwable) {
                timer?.stop(
                    Timer.builder(metricName)
                        .addDefaultTags(response.request)
                        .tags(
                            Tags.of(
                                Outcome.CLIENT_ERROR.asTag(),
                                Tag.of(TAG_STATUS, TAG_VALUE_UNKNOWN),
                                cause.toTag(),
                            )
                        )
                        .register(meterRegistry)
                )
                throw cause
            }
        }
    }

private fun Throwable.toTag(): Tag =
    Tag.of(
        TAG_EXCEPTION,
        cause?.javaClass?.simpleName ?: javaClass.simpleName,
    )

private fun String.removeHostPart(host: String) = replace("^.*$host[^/]*".toRegex(), "")

private fun String.removeQueryPart() = replace(QUERY_PART_REGEX, "")

private object ResponseHook : ClientHook<suspend ResponseHook.Context.(response: HttpResponse) -> Unit> {

    class Context(private val context: PipelineContext<HttpResponse, Unit>) {
        suspend fun proceed() = context.proceed()
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(response: HttpResponse) -> Unit
    ) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) {
            handler(Context(this), subject)
        }
    }
}

private object SendHook : ClientHook<suspend SendHook.Context.(response: HttpRequestBuilder) -> Unit> {

    class Context(private val context: PipelineContext<Any, HttpRequestBuilder>) {
        suspend fun proceed() = context.proceed()
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(request: HttpRequestBuilder) -> Unit
    ) {
        client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
            handler(Context(this), context)
        }
    }
}

private object ReceiveHook : ClientHook<suspend ReceiveHook.Context.(call: HttpClientCall) -> Unit> {

    class Context(private val context: PipelineContext<HttpResponseContainer, HttpClientCall>) {
        suspend fun proceed() = context.proceed()
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(call: HttpClientCall) -> Unit
    ) {
        client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
            handler(Context(this), context)
        }
    }
}
