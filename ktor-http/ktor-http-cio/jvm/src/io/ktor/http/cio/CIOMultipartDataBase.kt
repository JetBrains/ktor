/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

/**
 * Represents a multipart data object that does parse and convert parts to ktor's [PartData]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.cio.CIOMultipartDataBase)
 */
@InternalAPI
public class CIOMultipartDataBase(
    override val coroutineContext: CoroutineContext,
    channel: ByteReadChannel,
    contentType: CharSequence,
    contentLength: Long?,
    formFieldLimit: Long = 65536,
) : MultiPartData, CoroutineScope {
    // keep a reference to the previous part, so that we can
    // close the body if the next is retrieved without reading
    private var previousPart: PartData? = null

    private val events: ReceiveChannel<MultipartEvent> =
        parseMultipart(channel, contentType, contentLength, formFieldLimit)

    override suspend fun readPart(): PartData? {
        previousPart?.dispose?.invoke()

        while (true) {
            val event = events.tryReceive().getOrNull() ?: break
            eventToData(event)?.let {
                previousPart = it
                return it
            }
        }

        return readPartSuspend()
    }

    private suspend fun readPartSuspend(): PartData? {
        try {
            while (true) {
                val event = events.receive()
                eventToData(event)?.let { return it }
            }
        } catch (t: ClosedReceiveChannelException) {
            return null
        }
    }

    private suspend fun eventToData(event: MultipartEvent): PartData? {
        return try {
            when (event) {
                is MultipartEvent.MultipartPart -> partToData(event)
                else -> {
                    event.release()
                    null
                }
            }
        } catch (cause: Throwable) {
            event.release()
            throw cause
        }
    }

    private suspend fun partToData(part: MultipartEvent.MultipartPart): PartData {
        val headers = part.headers.await()

        val contentDisposition = headers["Content-Disposition"]?.let { ContentDisposition.parse(it.toString()) }
        val filename = contentDisposition?.parameter("filename")

        val body = part.body
        if (filename == null) {
            val packet = body.readRemaining() // formFieldLimit.toLong())
//            if (!body.exhausted()) {
//                val cause = IllegalStateException("Form field size limit exceeded: $formFieldLimit")
//                body.cancel(cause)
//                throw cause
//            }

            packet.use {
                return PartData.FormItem(it.readText(), { part.release() }, CIOHeaders(headers))
            }
        }

        return PartData.FileItem(
            { part.body },
            { part.release() },
            CIOHeaders(headers)
        )
    }
}
