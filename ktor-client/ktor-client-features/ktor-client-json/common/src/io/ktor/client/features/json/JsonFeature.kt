package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.io.*


/**
 * Platform default serializer.
 *
 * Uses service loader on jvm.
 * Consider to add one of the following dependencies:
 * - ktor-client-gson
 * - ktor-client-json
 */
expect fun defaultSerializer(): JsonSerializer

/**
 * [HttpClient] feature that serializes/de-serializes as JSON custom objects
 * to request and from response bodies using a [serializer].
 *
 * The default [serializer] is [GsonSerializer].
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is `application/json`
 */
class JsonFeature(
    val serializer: JsonSerializer,
    val allowedContentTypes: List<ContentType>
) {
    class Config {
        /**
         * Serialized that will be used for serializing requests bodies,
         * and de-serializing response bodies when Content-Type matches `application/json`.
         *
         * Default value is [defultSerializer]
         */
        var serializer: JsonSerializer? = null
        var allowedContentTypes: List<ContentType>? = null
    }

    companion object Feature : HttpClientFeature<Config, JsonFeature> {
        override val key: AttributeKey<JsonFeature> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonFeature =
            JsonFeature(
                Config().apply(block).serializer ?: defaultSerializer(),
                Config().apply(block).allowedContentTypes ?: listOf(ContentType.Application.Json)
            )

        override fun install(feature: JsonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                feature.allowedContentTypes.forEach { context.accept(it) }

                if (feature.allowedContentTypes.none { context.contentType()?.match(it) == true })
                    return@intercept

                context.headers.remove(HttpHeaders.ContentType)

                if (payload is EmptyContent) {
                    proceedWith(feature.serializer.write(Unit))
                    return@intercept
                }

                proceedWith(feature.serializer.write(payload))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept
                if (context.response.contentType()?.match(ContentType.Application.Json) != true) return@intercept
                try {
                    proceedWith(HttpResponseContainer(info, feature.serializer.read(info, body.readRemaining())))
                } finally {
                    context.close()
                }
            }
        }
    }
}

/**
 * Install [JsonFeature].
 */
fun HttpClientConfig<*>.Json(block: JsonFeature.Config.() -> Unit) {
    install(JsonFeature, block)
}
