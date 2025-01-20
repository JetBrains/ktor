/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.PipelineRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.utils.io.ByteReadChannel

internal class ExceptionPageContent(call: ApplicationCall, cause: Throwable) : OutgoingContent.ReadChannelContent() {

    
    private val responsePage: String = buildString {
        val request = call.request
        append("<html><body><h1>Internal Server Error</h1><h2>Request Information:</h2><pre>")
        append("Method: ${request.httpMethod}\n")
        append("Path: ${request.path()}\n")
        append("Parameters: ${request.rawQueryParameters}\n")
        append("From origin: ${request.origin}\n")
        append("</pre><h2>Stack Trace:</h2><pre>")
        
        val stackTrace = cause.stackTraceToString().lines()
        stackTrace.forEach { element ->
            append("<span style=\"color:blue;\">$element</span><br>")
        }
        var currentCause = cause.cause
        while (currentCause != null) {
            append("<br>Caused by:<br>")
            val causeStack = currentCause.stackTraceToString().lines()
            causeStack.forEach { element ->
                append("<span style=\"color:green;\">$element</span><br>")
            }
            currentCause = currentCause.cause
        }
        append("</pre></body></html>")
    }
    
    
    override fun readFrom(): ByteReadChannel = ByteReadChannel(responsePage)
}
