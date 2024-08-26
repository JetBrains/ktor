/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.internal.*
import io.ktor.server.logging.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * Default engine pipeline for all engines. Use it only if you are writing your own application engine implementation.
 */
public fun defaultEnginePipeline(config: ApplicationConfig, developmentMode: Boolean): EnginePipeline {
    val pipeline = EnginePipeline(developmentMode)

    configureShutdownUrl(config, pipeline)

    pipeline.intercept(EnginePipeline.Call) {
        try {
            call.application.execute(call)
        } catch (error: ChannelIOException) {
            call.application.mdcProvider.withMDCBlock(call) {
                call.application.environment.logFailure(call, error)
            }
        } catch (error: Throwable) {
            handleFailure(call, error)
        } finally {
            try {
                call.request.receiveChannel().discard()
            } catch (ignore: Throwable) {
            }
        }
    }

    return pipeline
}

/**
 * Logs the [error] and responds with an appropriate error status code.
 */
public suspend fun handleFailure(call: ApplicationCall, error: Throwable) {
    logError(call, error)
    tryRespondError(call, defaultExceptionStatusCode(error) ?: HttpStatusCode.InternalServerError)
}

/**
 * Logs the [error] with MDC setup.
 */
public suspend fun logError(call: ApplicationCall, error: Throwable) {
    call.application.mdcProvider.withMDCBlock(call) {
        call.application.environment.logFailure(call, error)
    }
}

/**
 * Map [cause] to the corresponding status code or `null` if no default exception mapping for this [cause] type
 */
public fun defaultExceptionStatusCode(cause: Throwable): HttpStatusCode? {
    return when (cause) {
        is BadRequestException -> HttpStatusCode.BadRequest
        is NotFoundException -> HttpStatusCode.NotFound
        is UnsupportedMediaTypeException -> HttpStatusCode.UnsupportedMediaType
        is PayloadTooLargeException -> HttpStatusCode.PayloadTooLarge
        is TimeoutException, is TimeoutCancellationException -> HttpStatusCode.GatewayTimeout
        else -> null
    }
}

private suspend fun tryRespondError(call: ApplicationCall, statusCode: HttpStatusCode) {
    try {
        call.respond(statusCode)
    } catch (ignore: BaseApplicationResponse.ResponseAlreadySentException) {
    }
}

private fun ApplicationEnvironment.logFailure(call: ApplicationCall, cause: Throwable) {
    try {
        val status = call.response.status() ?: "Unhandled"
        val logString = try {
            call.request.toLogString()
        } catch (cause: Throwable) {
            "(request error: $cause)"
        }

        val infoString = "$status: $logString. Exception ${cause::class}: ${cause.message}"
        when (cause) {
            is CancellationException,
            is ClosedChannelException,
            is ChannelIOException,
            is IOException,
            is BadRequestException,
            is NotFoundException,
            is PayloadTooLargeException,
            is UnsupportedMediaTypeException -> log.debug(infoString, cause)

            else -> log.error("$status: $logString", cause)
        }
    } catch (oom: OutOfMemoryError) {
        try {
            log.error(cause)
        } catch (oomAttempt2: OutOfMemoryError) {
            printError("OutOfMemoryError: ")
            printError(cause.message)
            printError("\n")
        }
    }
}
