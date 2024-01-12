/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

internal fun PluginBuilder<ContentNegotiationConfig>.convertRequestBody() {
    onCallReceive { call ->
        val registrations = pluginConfig.registrations
        val requestedType = call.receiveType

        if (requestedType.type in pluginConfig.ignoredTypes) {
            LOGGER.trace(
                "Skipping for request type ${requestedType.type} because the type is ignored."
            )
            return@onCallReceive
        }

        transformBody { body: ByteReadChannel ->
            val requestContentType = try {
                call.request.contentType().withoutParameters()
            } catch (parseFailure: BadContentTypeFormatException) {
                throw BadRequestException(
                    "Illegal Content-Type header format: ${call.request.headers[HttpHeaders.ContentType]}",
                    parseFailure
                )
            }

            val charset = call.request.contentCharset() ?: Charsets.UTF_8
            for (registration in registrations) {
                return@transformBody convertBody(body, charset, registration, requestedType, requestContentType)
                    ?: continue
            }

            LOGGER.trace("No suitable content converter found for request type ${requestedType.type}")
            return@transformBody body
        }
    }
}

private suspend fun convertBody(
    body: ByteReadChannel,
    charsets: Charset,
    registration: ConverterRegistration,
    receiveType: TypeInfo,
    requestContentType: ContentType
): Any? {
    if (!requestContentType.match(registration.contentType.withoutParameters())) {
        LOGGER.trace(
            "Skipping content converter for request type ${receiveType.type} because " +
                "content type $requestContentType does not match ${registration.contentType}"
        )
        return null
    }

    val converter = registration.converter
    val convertedBody = try {
        converter.deserialize(charsets, receiveType, body)
    } catch (cause: Throwable) {
        throw BadRequestException("Failed to convert request body to ${receiveType.type}", cause)
    }

    return when {
        convertedBody != null -> convertedBody
        !body.isClosedForRead -> null
        receiveType.kotlinType?.isMarkedNullable == true -> NullBody
        else -> null
    }
}
