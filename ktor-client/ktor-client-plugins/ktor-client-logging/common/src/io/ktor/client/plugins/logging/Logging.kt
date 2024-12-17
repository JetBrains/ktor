/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private val ClientCallLogger = AttributeKey<HttpClientCallLogger>("CallLogger")
private val DisableLogging = AttributeKey<Unit>("DisableLogging")

/**
 * A configuration for the [Logging] plugin.
 */
@KtorDsl
public class LoggingConfig {
    internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
    internal val sanitizedHeaders = mutableListOf<SanitizedHeader>()

    private var _logger: Logger? = null

    public var standardFormat: Boolean = false

    /**
     * Specifies a [Logger] instance.
     */
    public var logger: Logger
        get() = _logger ?: Logger.DEFAULT
        set(value) {
            _logger = value
        }

    /**
     * Specifies the logging level.
     */
    public var level: LogLevel = LogLevel.HEADERS

    /**
     * Allows you to filter log messages for calls matching a [predicate].
     */
    public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        filters.add(predicate)
    }

    /**
     * Allows you to sanitize sensitive headers to avoid their values appearing in the logs.
     * In the example below, Authorization header value will be replaced with '***' when logging:
     * ```kotlin
     * sanitizeHeader { header -> header == HttpHeaders.Authorization }
     * ```
     */
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        sanitizedHeaders.add(SanitizedHeader(placeholder, predicate))
    }
}

/**
 * A client's plugin that provides the capability to log HTTP calls.
 *
 * You can learn more from [Logging](https://ktor.io/docs/client-logging.html).
 */
@OptIn(InternalAPI::class)
public val Logging: ClientPlugin<LoggingConfig> = createClientPlugin("Logging", ::LoggingConfig) {
    val logger: Logger = pluginConfig.logger
    val level: LogLevel = pluginConfig.level
    val filters: List<(HttpRequestBuilder) -> Boolean> = pluginConfig.filters
    val sanitizedHeaders: List<SanitizedHeader> = pluginConfig.sanitizedHeaders
    val stdFormat = pluginConfig.standardFormat

    fun shouldBeLogged(request: HttpRequestBuilder): Boolean = filters.isEmpty() || filters.any { it(request) }

    fun logRequestStdFormat(request: HttpRequestBuilder) {
        if (level == LogLevel.NONE) return

        val uri = URLBuilder().takeFrom(request.url).build().pathQuery()

        if (request.method == HttpMethod.Get) {
            logger.log("--> ${request.method.value} $uri")
        } else {
            val headers = HeadersBuilder().apply { appendAll(request.headers) }.build()

            val body = request.body
            var contentLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength == null && request.method != HttpMethod.Get && body is OutgoingContent && body.contentLength != null) {
                contentLength = body.contentLength
            }

            if (level.headers && contentLength != null) {
                logger.log("--> ${request.method.value} $uri")
            } else if (level.info && contentLength != null) {
                logger.log("--> ${request.method.value} $uri ($contentLength-byte body)")
            } else if (request.body is OutgoingContent.WriteChannelContent || request.body is OutgoingContent.ReadChannelContent) {
                logger.log("--> ${request.method.value} $uri (unknown-byte body)")
            } else {
                val size = calcRequestBodySize(request.body, headers)
                logger.log("--> ${request.method.value} $uri ($size-byte body)")
            }
        }

        if (level.headers || level == LogLevel.BODY) {
            val headers = HeadersBuilder().apply {
                val body = request.body
                if (body is OutgoingContent && request.method != HttpMethod.Get && body !is EmptyContent) {
                    body.contentType?.let {
                        appendIfNameAbsent(HttpHeaders.ContentType, it.toString())
                    }
                    body.contentLength?.let {
                        appendIfNameAbsent(HttpHeaders.ContentLength, it.toString())
                    }
                }
                appendAll(request.headers)
            }.build()

            for ((name, values) in headers.entries()) {
                logger.log("$name: ${values.joinToString(separator = ", ")}")
            }

            logger.log("--> END ${request.method.value}")
        }
    }

    fun logResponseStdFormat(response: HttpResponse) {
        if (level == LogLevel.NONE) return

        var contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val request = response.request
        val duration = response.responseTime.timestamp - response.requestTime.timestamp

        if (level.headers && contentLength != null) {
            logger.log("<-- ${response.status} ${request.url.pathQuery()} ${response.version} (${duration}ms)")
        } else if (level.info && contentLength != null) {
            logger.log("<-- ${response.status} ${request.url.pathQuery()} ${response.version} (${duration}ms, $contentLength-byte body)")
        } else {
            logger.log("<-- ${response.status} ${request.url.pathQuery()} ${response.version} (${duration}ms, unknown-byte body)")
        }

        if (level.headers || level == LogLevel.BODY) {
            for ((name, values) in response.headers.entries()) {
                logger.log("$name: ${values.joinToString(separator = ", ")}")
            }

            logger.log("<-- END HTTP")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun logRequestBody(
        content: OutgoingContent,
        logger: HttpClientCallLogger
    ): OutgoingContent {
        val requestLog = StringBuilder()
        requestLog.appendLine("BODY Content-Type: ${content.contentType}")

        val charset = content.contentType?.charset() ?: Charsets.UTF_8

        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Default + MDCContext()) {
            try {
                val text = channel.tryReadText(charset) ?: "[request body omitted]"
                requestLog.appendLine("BODY START")
                requestLog.appendLine(text)
                requestLog.append("BODY END")
            } finally {
                logger.logRequest(requestLog.toString())
                logger.closeRequestLog()
            }
        }

        return content.observe(channel)
    }

    fun logRequestException(context: HttpRequestBuilder, cause: Throwable) {
        if (level.info) {
            logger.log("REQUEST ${Url(context.url)} failed with exception: $cause")
        }
    }

    suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        val content = request.body as OutgoingContent
        val callLogger = HttpClientCallLogger(logger)
        request.attributes.put(ClientCallLogger, callLogger)

        val message = buildString {
            if (level.info) {
                appendLine("REQUEST: ${Url(request.url)}")
                appendLine("METHOD: ${request.method}")
            }

            if (level.headers) {
                appendLine("COMMON HEADERS")
                logHeaders(request.headers.entries(), sanitizedHeaders)

                appendLine("CONTENT HEADERS")
                val contentLengthPlaceholder = sanitizedHeaders
                    .firstOrNull { it.predicate(HttpHeaders.ContentLength) }
                    ?.placeholder
                val contentTypePlaceholder = sanitizedHeaders
                    .firstOrNull { it.predicate(HttpHeaders.ContentType) }
                    ?.placeholder
                content.contentLength?.let {
                    logHeader(HttpHeaders.ContentLength, contentLengthPlaceholder ?: it.toString())
                }
                content.contentType?.let {
                    logHeader(HttpHeaders.ContentType, contentTypePlaceholder ?: it.toString())
                }
                logHeaders(content.headers.entries(), sanitizedHeaders)
            }
        }

        if (message.isNotEmpty()) {
            callLogger.logRequest(message)
        }

        if (message.isEmpty() || !level.body) {
            callLogger.closeRequestLog()
            return null
        }

        return logRequestBody(content, callLogger)
    }

    fun logResponseException(log: StringBuilder, request: HttpRequest, cause: Throwable) {
        if (!level.info) return
        log.append("RESPONSE ${request.url} failed with exception: $cause")
    }

    on(SendHook) { request ->
        if (!shouldBeLogged(request)) {
            request.attributes.put(DisableLogging, Unit)
            return@on
        }

        if (stdFormat) {
            logRequestStdFormat(request)
            return@on
        }

        val loggedRequest = try {
            logRequest(request)
        } catch (_: Throwable) {
            null
        }

        try {
            proceedWith(loggedRequest ?: request.body)
        } catch (cause: Throwable) {
            logRequestException(request, cause)
            throw cause
        } finally {
        }
    }

    on(ResponseAfterEncodingHook) { response ->
        if (stdFormat) {
            logResponseStdFormat(response)
        }
    }

    on(ResponseHook) { response ->
        if (stdFormat) return@on

        if (level == LogLevel.NONE || response.call.attributes.contains(DisableLogging)) return@on

        val callLogger = response.call.attributes[ClientCallLogger]
        val header = StringBuilder()

        var failed = false
        try {
            logResponseHeader(header, response.call.response, level, sanitizedHeaders)
            proceed()
        } catch (cause: Throwable) {
            logResponseException(header, response.call.request, cause)
            failed = true
            throw cause
        } finally {
            callLogger.logResponseHeader(header.toString())
            if (failed || !level.body) callLogger.closeResponseLog()
        }
    }

    on(ReceiveHook) { call ->
        if (stdFormat) {
            return@on
        }

        if (level == LogLevel.NONE || call.attributes.contains(DisableLogging)) {
            return@on
        }

        try {
            proceed()
        } catch (cause: Throwable) {
            val log = StringBuilder()
            val callLogger = call.attributes[ClientCallLogger]
            logResponseException(log, call.request, cause)
            callLogger.logResponseException(log.toString())
            callLogger.closeResponseLog()
            throw cause
        }
    }

    if (!level.body) return@createClientPlugin

    @OptIn(InternalAPI::class)
    val observer: ResponseHandler = observer@{
        if (level == LogLevel.NONE || it.call.attributes.contains(DisableLogging)) {
            return@observer
        }

        val callLogger = it.call.attributes[ClientCallLogger]
        val log = StringBuilder()
        try {
            logResponseBody(log, it.contentType(), it.rawContent)
        } catch (_: Throwable) {
        } finally {
            callLogger.logResponseBody(log.toString().trim())
            callLogger.closeResponseLog()
        }
    }

    ResponseObserver.install(ResponseObserver.prepare { onResponse(observer) }, client)
}

private fun Url.pathQuery(): String {
    return buildString {
        if (encodedPath.isEmpty()) {
            append("/")
        } else {
            append(encodedPath)
        }

        if (encodedQuery.isEmpty()) return@buildString

        append("?")
        append(encodedQuery)
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun calcRequestBodySize(content: Any, headers: Headers): Long {
    check(content is OutgoingContent)

    return when(content) {
        is OutgoingContent.ByteArrayContent -> content.bytes().size.toLong()
        is OutgoingContent.ContentWrapper -> calcRequestBodySize(content.delegate(), content.headers)
        is OutgoingContent.NoContent -> 0
        is OutgoingContent.ProtocolUpgrade -> 0
        else -> error("Unable to calculate the size for type ${content::class.simpleName}")
    }
}

/**
 * Configures and installs [Logging] in [HttpClient].
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.Logging(block: LoggingConfig.() -> Unit = {}) {
    install(Logging, block)
}

internal class SanitizedHeader(
    val placeholder: String,
    val predicate: (String) -> Boolean
)

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

private object ResponseAfterEncodingHook : ClientHook<suspend ResponseAfterEncodingHook.Context.(response: HttpResponse) -> Unit> {

    class Context(private val context: PipelineContext<HttpResponse, Unit>) {
        suspend fun proceedWith(response: HttpResponse) = context.proceedWith(response)
    }

    override fun install(
        client: HttpClient,
        handler: suspend Context.(response: HttpResponse) -> Unit
    ) {
        val afterState = PipelinePhase("AfterState")
        client.receivePipeline.insertPhaseAfter(HttpReceivePipeline.State, afterState)
        client.receivePipeline.intercept(afterState) {
            handler(Context(this), subject)
        }
    }
}

private object SendHook : ClientHook<suspend SendHook.Context.(response: HttpRequestBuilder) -> Unit> {

    class Context(private val context: PipelineContext<Any, HttpRequestBuilder>) {
        suspend fun proceedWith(content: Any) = context.proceedWith(content)
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
