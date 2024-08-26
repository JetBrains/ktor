/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.requestvalidation

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.io.*

/**
 * A result of validation.
 */
public sealed class ValidationResult {
    /**
     * A successful result of validation.
     */
    public data object Valid : ValidationResult()

    /**
     * An unsuccessful result of validation. All errors are stored in the [reasons] list.
     */
    public class Invalid(
        /**
         * List of errors.
         */
        public val reasons: List<String>
    ) : ValidationResult() {
        public constructor (reason: String) : this(listOf(reason))
    }
}

/**
 * A validator that should be registered with [RequestValidation] plugin
 */
public interface Validator {
    /**
     * Validates the [value].
     */
    public suspend fun validate(value: Any): ValidationResult

    /**
     * Checks if the [value] should be checked by this validator.
     */
    public fun filter(value: Any): Boolean
}

/**
 * A plugin that checks a request body using [Validator].
 * Example:
 * ```
 * install(RequestValidation) {
 *     validate<String> {
 *         if (!it.startsWith("+")) ValidationResult.Invalid("$it should start with \"+\"")
 *         else ValidationResult.Valid
 *     }
 * }
 * install(StatusPages) {
 *     exception<RequestValidationException> { call, cause ->
 *         call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString())
 *     }
 * }
 * ```
 */
public val RequestValidation: RouteScopedPlugin<RequestValidationConfig> = createRouteScopedPlugin(
    "RequestValidation",
    ::RequestValidationConfig
) {

    val validators = pluginConfig.validators

    on(RequestBodyTransformed) { content ->
        @Suppress("UNCHECKED_CAST")
        val failures = validators.filter { it.filter(content) }
            .map { it.validate(content) }
            .filterIsInstance<ValidationResult.Invalid>()
        if (failures.isNotEmpty()) {
            throw RequestValidationException(content, failures.flatMap { it.reasons })
        }
    }

    if (!pluginConfig.validateContentLength) return@createRouteScopedPlugin

    on(ReceiveRequestBytes) { call, body ->
        val contentLength = call.request.contentLength() ?: return@on body

        return@on application.writer {
            val count = body.copyTo(channel)
            if (count != contentLength) {
                throw IOException("Content length mismatch. Actual $count, expected $contentLength.")
            }
        }.channel
    }
}

/**
 * Thrown when validation fails.
 * @property value - invalid request body
 * @property reasons - combined reasons of all validation failures for this request
 */
public class RequestValidationException(
    public val value: Any,
    public val reasons: List<String>
) : IllegalArgumentException("Validation failed for $value. Reasons: ${reasons.joinToString(".")}")

private object RequestBodyTransformed : Hook<suspend (content: Any) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (content: Any) -> Unit
    ) {
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.After) {
            handler(subject)
        }
    }
}
