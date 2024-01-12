/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.plugins.json

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.*

internal val DefaultCommonIgnoredTypes: Set<KClass<*>> = setOf(
    ByteArray::class,
    String::class,
    HttpStatusCode::class,
    ByteReadChannel::class,
    OutgoingContent::class
)

internal expect val DefaultIgnoredTypes: Set<KClass<*>>

/**
 * Platform default serializer.
 *
 * Uses service loader on jvm.
 * Consider to add one of the following dependencies:
 * - ktor-client-gson
 * - ktor-client-json
 */
public expect fun defaultSerializer(): JsonSerializer

/**
 * [HttpClient] plugin that serializes/de-serializes as JSON custom objects
 * to request and from response bodies using a [serializer].
 *
 * The default [serializer] is [GsonSerializer].
 *
 * The default [acceptContentTypes] is a list which contains [ContentType.Application.Json]
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is one of [acceptContentTypes] list (`application/json` by default).
 *
 * @property serializer that is used to serialize and deserialize request/response bodies
 * @property acceptContentTypes that are allowed when receiving content
 */
@Deprecated(
    "Please use ContentNegotiation plugin: https://ktor.io/docs/migrating-2.html#serialization-client",
    level = DeprecationLevel.ERROR
)
public class JsonPlugin internal constructor(
    public val serializer: JsonSerializer,
    public val acceptContentTypes: List<ContentType> = listOf(ContentType.Application.Json),
    private val receiveContentTypeMatchers: List<ContentTypeMatcher> = listOf(JsonContentTypeMatcher()),
    private val ignoredTypes: Set<KClass<*>> = DefaultCommonIgnoredTypes + DefaultIgnoredTypes
) {
    internal constructor(config: Config) : this(
        config.serializer ?: defaultSerializer(),
        config.acceptContentTypes,
        config.receiveContentTypeMatchers
    )

    /**
     * [JsonPlugin] configuration that is used during installation
     */
    @KtorDsl
    public class Config {

        internal val ignoredTypes: MutableSet<KClass<*>> =
            (DefaultIgnoredTypes + DefaultCommonIgnoredTypes).toMutableSet()

        /**
         * Serializer that will be used for serializing requests and deserializing response bodies.
         *
         * Default value for [serializer] is [defaultSerializer].
         */
        public var serializer: JsonSerializer? = null

        /**
         * Backing field with mutable list of content types that are handled by this plugin.
         */
        private val _acceptContentTypes: MutableList<ContentType> = mutableListOf(ContentType.Application.Json)
        private val _receiveContentTypeMatchers: MutableList<ContentTypeMatcher> =
            mutableListOf(JsonContentTypeMatcher())

        /**
         * List of content types that are handled by this plugin.
         * It also affects `Accept` request header value.
         * Please note that wildcard content types are supported but no quality specification provided.
         */
        public var acceptContentTypes: List<ContentType>
            set(value) {
                require(value.isNotEmpty()) { "At least one content type should be provided to acceptContentTypes" }

                _acceptContentTypes.clear()
                _acceptContentTypes.addAll(value)
            }
            get() = _acceptContentTypes

        /**
         * List of content type matchers that are handled by this plugin.
         * Please note that wildcard content types are supported but no quality specification provided.
         */
        public var receiveContentTypeMatchers: List<ContentTypeMatcher>
            set(value) {
                require(value.isNotEmpty()) { "At least one content type should be provided to acceptContentTypes" }
                _receiveContentTypeMatchers.clear()
                _receiveContentTypeMatchers.addAll(value)
            }
            get() = _receiveContentTypeMatchers

        /**
         * Adds accepted content types. Be aware that [ContentType.Application.Json] accepted by default is removed from
         * the list if you use this function to provide accepted content types.
         * It also affects `Accept` request header value.
         */
        public fun accept(vararg contentTypes: ContentType) {
            _acceptContentTypes += contentTypes
        }

        /**
         * Adds accepted content types. Existing content types will not be removed.
         */
        public fun receive(matcher: ContentTypeMatcher) {
            _receiveContentTypeMatchers += matcher
        }

        /**
         * Adds a type to the list of types that should be ignored by [ContentNegotiation].
         *
         * The list contains the [HttpStatusCode], [ByteArray], [String] and streaming types by default.
         */
        public inline fun <reified T> ignoreType() {
            ignoreType(T::class)
        }

        /**
         * Remove [T] from the list of types that should be ignored by [ContentNegotiation].
         */
        public inline fun <reified T> removeIgnoredType() {
            removeIgnoredType(T::class)
        }

        /**
         * Remove [type] from the list of types that should be ignored by [ContentNegotiation].
         */
        public fun removeIgnoredType(type: KClass<*>) {
            ignoredTypes.remove(type)
        }

        /**
         * Adds a [type] to the list of types that should be ignored by [ContentNegotiation].
         *
         * The list contains the [HttpStatusCode], [ByteArray], [String] and streaming types by default.
         */
        public fun ignoreType(type: KClass<*>) {
            ignoredTypes.add(type)
        }

        /**
         * Clear all configured ignored types including defaults.
         */
        public fun clearIgnoredTypes() {
            ignoredTypes.clear()
        }
    }

    internal fun canHandle(contentType: ContentType): Boolean {
        val accepted = acceptContentTypes.any { contentType.match(it) }
        val matchers = receiveContentTypeMatchers

        return accepted || matchers.any { matcher -> matcher.contains(contentType) }
    }

    /**
     * Companion object for plugin installation
     */
    public companion object Plugin : HttpClientPlugin<Config, JsonPlugin> {
        override val key: AttributeKey<JsonPlugin> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonPlugin {
            val config = Config().apply(block)
            val serializer = config.serializer ?: defaultSerializer()
            val allowedContentTypes = config.acceptContentTypes.toList()
            val receiveContentTypeMatchers = config.receiveContentTypeMatchers
            val ignoredTypes = config.ignoredTypes

            return JsonPlugin(serializer, allowedContentTypes, receiveContentTypeMatchers, ignoredTypes)
        }

        override fun install(plugin: JsonPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                plugin.acceptContentTypes.forEach { context.accept(it) }

                if (payload::class in plugin.ignoredTypes) return@intercept
                val contentType = context.contentType() ?: return@intercept
                if (!plugin.canHandle(contentType)) return@intercept

                context.headers.remove(HttpHeaders.ContentType)

                val serializedContent = when (payload) {
                    Unit -> EmptyContent
                    is EmptyContent -> EmptyContent
                    else -> plugin.serializer.write(payload, contentType)
                }

                proceedWith(serializedContent)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept
                if (info.type in plugin.ignoredTypes) return@intercept

                val contentType = context.response.contentType() ?: return@intercept
                if (!plugin.canHandle(contentType)) return@intercept

                val parsedBody = plugin.serializer.read(info, body.readRemaining())
                val response = HttpResponseContainer(info, parsedBody)
                proceedWith(response)
            }
        }
    }
}

/**
 * Install [JsonPlugin].
 */
@Deprecated(
    "Please use ContentNegotiation plugin: https://ktor.io/docs/migrating-2.html#serialization-client",
    level = DeprecationLevel.ERROR
)
public fun HttpClientConfig<*>.Json(block: JsonPlugin.Config.() -> Unit) {
    install(JsonPlugin, block)
}
