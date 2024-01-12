/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal fun Application.serverSentEvents() {
    routing {
        route("/sse") {
            get("/hello") {
                val delayMillis = call.parameters["delay"]?.toLong() ?: 0
                delay(delayMillis)

                val times = call.parameters["times"]?.toInt() ?: 1
                val events = flow {
                    repeat(times) {
                        emit(it)
                    }
                }.map {
                    SseEvent("hello\nfrom server", "hello $it", "$it")
                }
                call.respondSseEvents(events)
            }
            get("/comments") {
                val times = call.parameters["times"]?.toInt() ?: 1
                var isComment = false
                val events = flow {
                    repeat(times * 2) {
                        emit(it)
                    }
                }.map {
                    isComment = !isComment
                    if (isComment) {
                        SseEvent(comments = "$it")
                    } else {
                        SseEvent(data = "$it")
                    }
                }
                call.respondSseEvents(events)
            }
            get("/auth") {
                val token = call.request.headers["Authorization"]
                if (token.isNullOrEmpty() || token.contains("invalid")) {
                    call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"TestServer\"")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                call.respondSseEvents(
                    flow {
                        emit(SseEvent("hello after refresh"))
                    }
                )
            }
            get("/content_type_with_charset") {
                val events = flow {
                    emit(SseEvent("hello\nfrom server", "hello 0", "0"))
                }
                val contentType = ContentType.Text.EventStream.withCharset(Charsets.UTF_8)
                call.respondBytesWriter(contentType = contentType) {
                    writeSseEvents(events)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondSseEvents(events: Flow<SseEvent>) {
    respondBytesWriter(contentType = ContentType.Text.EventStream) {
        writeSseEvents(events)
    }
}

private suspend fun ByteWriteChannel.writeSseEvents(events: Flow<SseEvent>): Unit = events.collect { event ->
    if (event.id != null) {
        writeStringUtf8("id: ${event.id}\n")
    }
    if (event.event != null) {
        writeStringUtf8("event: ${event.event}\n")
    }
    if (event.data != null) {
        for (dataLine in event.data.lines()) {
            writeStringUtf8("data: $dataLine\n")
        }
    }
    if (event.retry != null) {
        writeStringUtf8("retry: ${event.retry}\n")
    }

    if (event.comments != null) {
        for (dataLine in event.comments.lines()) {
            writeStringUtf8(": $dataLine\n")
        }
    }
    writeStringUtf8("\n")
    flush()
}

private data class SseEvent(
    val data: String? = null,
    val event: String? = null,
    val id: String? = null,
    val retry: Long? = null,
    val comments: String? = null
)
