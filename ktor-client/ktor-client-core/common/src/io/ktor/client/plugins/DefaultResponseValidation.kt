/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.charsets.*
import kotlin.jvm.*
import kotlin.native.concurrent.*

private val ValidateMark = AttributeKey<Unit>("ValidateMark")
private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.DefaultResponseValidation")

/**
 * Default response validation.
 * Check the response status code in range (0..299).
 */
public fun HttpClientConfig<*>.addDefaultResponseValidation() {
    HttpResponseValidator {
        expectSuccess = this@addDefaultResponseValidation.expectSuccess

        validateResponse { response ->
            val expectSuccess = response.call.attributes[ExpectSuccessAttributeKey]
            if (!expectSuccess) {
                LOGGER.trace("Skipping default response validation for ${response.call.request.url}")
                return@validateResponse
            }

            val statusCode = response.status.value
            val originCall = response.call
            if (statusCode < 300 || originCall.attributes.contains(ValidateMark)) {
                return@validateResponse
            }

            val exceptionCall = originCall.save().apply {
                attributes.put(ValidateMark, Unit)
            }

            val exceptionResponse = exceptionCall.response
            val exceptionResponseText = try {
                exceptionResponse.bodyAsText()
            } catch (_: MalformedInputException) {
                BODY_FAILED_DECODING
            }
            val exception = when (statusCode) {
                in 300..399 -> RedirectResponseException(exceptionResponse, exceptionResponseText)
                in 400..499 -> ClientRequestException(exceptionResponse, exceptionResponseText)
                in 500..599 -> ServerResponseException(exceptionResponse, exceptionResponseText)
                else -> ResponseException(exceptionResponse, exceptionResponseText)
            }
            LOGGER.trace("Default response validation for ${response.call.request.url} failed with $exception")
            throw exception
        }
    }
}

private const val NO_RESPONSE_TEXT: String = "<no response text provided>"
private const val BODY_FAILED_DECODING: String = "<body failed decoding>"
private const val DEPRECATED_EXCEPTION_CTOR: String = "Please, provide response text in constructor"

/**
 * Base for default response exceptions.
 * @param [response]: origin response
 */
public open class ResponseException(
    response: HttpResponse,
    cachedResponseText: String
) : IllegalStateException("Bad response: $response. Text: \"$cachedResponseText\"") {

    @Transient
    public val response: HttpResponse = response
}

/**
 * Unhandled redirect exception.
 */
public class RedirectResponseException(response: HttpResponse, cachedResponseText: String) :
    ResponseException(response, cachedResponseText) {

    override val message: String =
        "Unhandled redirect: ${response.call.request.method.value} ${response.call.request.url}. " +
            "Status: ${response.status}. Text: \"$cachedResponseText\""
}

/**
 * Server error exception.
 */
public class ServerResponseException(
    response: HttpResponse,
    cachedResponseText: String
) : ResponseException(response, cachedResponseText) {

    override val message: String = "Server error(${response.call.request.method.value} ${response.call.request.url}: " +
        "${response.status}. Text: \"$cachedResponseText\""
}

/**
 * Bad client request exception.
 */
public class ClientRequestException(
    response: HttpResponse,
    cachedResponseText: String
) : ResponseException(response, cachedResponseText) {

    override val message: String =
        "Client request(${response.call.request.method.value} ${response.call.request.url}) " +
            "invalid: ${response.status}. Text: \"$cachedResponseText\""
}
