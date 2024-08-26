/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.sse.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
public class DefaultClientSSESession(
    content: SSEClientContent,
    private var input: ByteReadChannel,
    override val coroutineContext: CoroutineContext,
) : SSESession {
    private var lastEventId: String? = null
    private var reconnectionTimeMillis = content.reconnectionTime.inWholeMilliseconds
    private val showCommentEvents = content.showCommentEvents
    private val showRetryEvents = content.showRetryEvents

    private val _incoming = channelFlow {
        while (true) {
            val event = input.parseEvent() ?: break

            if (event.isCommentsEvent() && !showCommentEvents) continue
            if (event.isRetryEvent() && !showRetryEvents) continue

            send(event)
        }
    }

    override val incoming: Flow<ServerSentEvent>
        get() = _incoming

    private suspend fun ByteReadChannel.parseEvent(): ServerSentEvent? {
        val data = StringBuilder()
        val comments = StringBuilder()
        var eventType: String? = null
        var curRetry: Long? = null
        var lastEventId: String? = this@DefaultClientSSESession.lastEventId

        var wasData = false
        var wasComments = false

        var line: String = readUTF8Line() ?: return null
        while (line.isBlank()) {
            line = readUTF8Line() ?: return null
        }

        while (true) {
            when {
                line.isBlank() -> {
                    this@DefaultClientSSESession.lastEventId = lastEventId

                    val event = ServerSentEvent(
                        if (wasData) data.toText() else null,
                        eventType,
                        lastEventId,
                        curRetry,
                        if (wasComments) comments.toText() else null
                    )

                    if (!event.isEmpty()) {
                        return event
                    }
                }

                line.startsWith(COLON) -> {
                    wasComments = true
                    comments.appendComment(line)
                }

                else -> {
                    val field = line.substringBefore(COLON)
                    val value = line.substringAfter(COLON, missingDelimiterValue = "").removePrefix(SPACE)
                    when (field) {
                        "event" -> eventType = value
                        "data" -> {
                            wasData = true
                            data.append(value).append(END_OF_LINE)
                        }

                        "retry" -> {
                            value.toLongOrNull()?.let {
                                reconnectionTimeMillis = it
                                curRetry = it
                            }
                        }

                        "id" -> if (!value.contains(NULL)) {
                            lastEventId = value
                        }
                    }
                }
            }
            line = readUTF8Line() ?: return null
        }
    }

    private fun StringBuilder.appendComment(comment: String) {
        append(comment.removePrefix(COLON).removePrefix(SPACE)).append(END_OF_LINE)
    }

    private fun StringBuilder.toText() = toString().removeSuffix(END_OF_LINE)

    private fun ServerSentEvent.isEmpty() =
        data == null && id == null && event == null && retry == null && comments == null

    private fun ServerSentEvent.isCommentsEvent() =
        data == null && event == null && id == null && retry == null && comments != null

    private fun ServerSentEvent.isRetryEvent() =
        data == null && event == null && id == null && comments == null && retry != null
}

private const val NULL = "\u0000"
