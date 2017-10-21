package io.ktor.jackson

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*

/**
 *    install(ContentNegotiation) {
 *       register(ContentType.Application.Json, JacksonConverter())
 *    }
 *
 *    to be able to modify the objectMapper (eg. using specific modules and/or serializers and/or
 *    configuration options, you could use the following (as seen in the ktor-samples):
 *
 *    install(ContentNegotiation) {
 *        val objectMapper = jacksonObjectMapper()
 *        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true)
 *        register(ContentType.Application.Json, JacksonConverter(objectMapper))
 *    }
 */
class JacksonConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : ContentConverter {
    private val contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, value: Any): Any? {
        return ConvertedContent(objectmapper.writeValueAsString(value), contentType)
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val value = request.value as? IncomingContent ?: return null
        val type = request.type
        return objectmapper.readValue(value.readText(), type.javaObjectType)
    }
}
