/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sse

import io.ktor.util.*

/**
 *  Server-sent event.
 *
 *  @property data data field of the event.
 *  @property event string identifying the type of event.
 *  @property id event ID.
 *  @property retry reconnection time, in milliseconds to wait before reconnecting.
 *  @property comments comment lines starting with a ':' character.
 */
public class ServerSentEvent(
    public val data: String? = null,
    public val event: String? = null,
    public val id: String? = null,
    public val retry: Long? = null,
    public val comments: String? = null
) {
    override fun toString(): String {
        return buildString {
            appendField("data", data)
            appendField("event", event)
            appendField("id", id)
            appendField("retry", retry)
            appendField("comments", comments)
        }
    }
}

@Suppress("KDocMissingDocumentation")
public class SSEException : IllegalStateException {
    public constructor(cause: Throwable?) : super(cause)
    public constructor(message: String) : super(message)
}

@OptIn(InternalAPI::class)
private fun <T> StringBuilder.appendField(name: String, value: T?) {
    if (value != null) {
        append("$name$COLON$SPACE$value$END_OF_LINE")
    }
}

@InternalAPI
public const val COLON: String = ":"

@InternalAPI
public const val SPACE: String = " "

@InternalAPI
public const val END_OF_LINE: String = "\r\n"
