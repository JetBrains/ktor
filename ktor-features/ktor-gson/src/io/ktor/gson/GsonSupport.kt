package io.ktor.gson

import com.google.gson.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

/**
 *    install(ContentNegotiation) {
 *       register(ContentType.Application.Json, GsonConverter())
 *    }
 *
 *    to be able to modify the gsonBuilder (eg. using specific serializers and/or
 *    configuration options, you could use the following (as seen in the ktor-samples):
 *
 *    install(ContentNegotiation) {
 *        gson {
 *            registerTypeAdapterFactory(GsonAdaptersMyDocument())
 *            setDateFormat(DateFormat.LONG)
 *            setPrettyPrinting()
 *        }
 *    }
 */
@Deprecated("GsonSupport is deprecated in favor of generic ContentNegotiation Feature")
class GsonSupport(val gson: Gson) {
    @Suppress("DEPRECATION")
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, GsonBuilder, GsonSupport> {
        override val key = AttributeKey<GsonSupport>("gson")

        override fun install(pipeline: ApplicationCallPipeline, configure: GsonBuilder.() -> Unit): GsonSupport {
            val gson = GsonBuilder().apply(configure).create()
            val feature = GsonSupport(gson)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) {
                if (it !is OutgoingContent && call.request.acceptItems().any { ContentType.Application.Json.match(it.value) }) {
                    proceedWith(feature.renderJsonContent(it))
                }
            }
            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                val contentType = call.request.contentType()
                if (contentType.match(ContentType.Application.Json)) {
                    val message = it.value as? IncomingContent ?: return@intercept
                    val json = message.readText()
                    val value = gson.fromJson(json, it.type.javaObjectType)
                    proceedWith(ApplicationReceiveRequest(it.type, value))
                }
            }
            return feature
        }
    }

    private fun renderJsonContent(model: Any): TextContent {
        val json = gson.toJson(model)
        return TextContent(json, ContentType.Application.Json.withCharset(Charsets.UTF_8))
    }
}


class GsonConverter(private val gson: Gson = Gson()) : ContentConverter {
    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
        return TextContent(gson.toJson(value), contentType.withCharset(context.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val value = request.value as? IncomingContent ?: return null
        val type = request.type
        return gson.fromJson(value.readText(), type.javaObjectType)
    }
}

fun ContentNegotiation.Configuration.gson(block: GsonBuilder.() -> Unit) {
    val builder = GsonBuilder()
    builder.apply(block)
    val converter = GsonConverter(builder.create())
    register(ContentType.Application.Json, converter)
}
