/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.partialcontent

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal sealed class PartialOutgoingContent(val original: ReadChannelContent) : OutgoingContent.ReadChannelContent() {
    override val status: HttpStatusCode? get() = original.status
    override val contentType: ContentType? get() = original.contentType
    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

    class Bypass(original: ReadChannelContent) : PartialOutgoingContent(original) {

        override val contentLength: Long?
            get() = original.contentLength

        override fun readFrom() = original.readFrom()

        override val headers by lazy(LazyThreadSafetyMode.NONE) {
            Headers.build {
                appendAll(original.headers)
                acceptRanges()
            }
        }
    }

    class Single(
        val get: Boolean,
        original: ReadChannelContent,
        val range: LongRange,
        val fullLength: Long
    ) : PartialOutgoingContent(original) {
        override val status: HttpStatusCode?
            get() = if (get) HttpStatusCode.PartialContent else original.status

        override val contentLength: Long get() = range.last - range.first + 1

        override fun readFrom(): ByteReadChannel = original.readFrom(range)

        override val headers by lazy(LazyThreadSafetyMode.NONE) {
            Headers.build {
                appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                acceptRanges()
                contentRange(range, fullLength)
            }
        }
    }

    class Multiple(
        override val coroutineContext: CoroutineContext,
        val get: Boolean,
        original: ReadChannelContent,
        val ranges: List<LongRange>,
        val length: Long,
        val boundary: String
    ) : PartialOutgoingContent(original), CoroutineScope {
        override val status: HttpStatusCode?
            get() = if (get) HttpStatusCode.PartialContent else original.status

        override val contentLength: Long = calculateMultipleRangesBodyLength(
            ranges,
            length,
            boundary,
            original.contentType.toString()
        )

        override val contentType: ContentType
            get() = ContentType.MultiPart.ByteRanges.withParameter(
                "boundary",
                boundary
            )

        override fun readFrom() = writeMultipleRangesImpl(
            { range -> original.readFrom(range) },
            ranges,
            length,
            boundary,
            original.contentType.toString()
        )

        override val headers by lazy(LazyThreadSafetyMode.NONE) {
            Headers.build {
                appendFiltered(original.headers) { name, _ ->
                    !name.equals(HttpHeaders.ContentType, true) && !name.equals(HttpHeaders.ContentLength, true)
                }
                acceptRanges()
            }
        }
    }

    protected fun HeadersBuilder.acceptRanges() {
        if (!contains(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)) {
            append(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)
        }
    }
}
