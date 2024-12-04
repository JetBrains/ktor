/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.statuspages

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

/**
 * Register a status page file(s) using [filePattern] for multiple status [code] list
 * @param code vararg list of status codes handled by this configuration
 * @param filePattern path to status file with optional `#` character(s) that will be replaced with numeric status code
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.plugins.statuspages.statusFile)
 */
public fun StatusPagesConfig.statusFile(vararg code: HttpStatusCode, filePattern: String) {
    status(*code) { call, status ->
        val path = filePattern.replace("#", status.value.toString())
        val message = call.resolveResource(path)
        if (message == null) {
            call.respond(HttpStatusCode.InternalServerError)
        } else {
            call.response.status(status)
            call.respond(message)
        }
    }
}

/**
 * Register an exception [handler] for the exception class [klass] and its children
 */
public fun <T : Throwable> StatusPagesConfig.exception(
    klass: Class<T>,
    handler: suspend PipelineContext<Unit, PipelineCall>.(T) -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    val cast = handler as suspend (ApplicationCall, Throwable) -> Unit

    exceptions[klass.kotlin] = cast
}
