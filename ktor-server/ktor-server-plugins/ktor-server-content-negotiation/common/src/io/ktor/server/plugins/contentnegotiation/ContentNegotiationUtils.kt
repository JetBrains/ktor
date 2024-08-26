/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

/**
 * Specifies which [converter] to use for a particular [contentType].
 * @param contentType is an instance of [ContentType] for this registration
 * @param converter is an instance of [ContentConverter] for this registration
 */
internal class ConverterRegistration(val contentType: ContentType, val converter: ContentConverter)

internal fun ApplicationCall.parseAcceptHeader(): List<ContentTypeWithQuality> {
    val acceptHeaderContent = request.header(HttpHeaders.Accept)

    return try {
        parseHeaderValue(acceptHeaderContent).map { ContentTypeWithQuality(ContentType.parse(it.value), it.quality) }
    } catch (parseFailure: BadContentTypeFormatException) {
        throw BadRequestException("Illegal Accept header format: $acceptHeaderContent", parseFailure)
    }
}

internal fun checkAcceptHeader(
    acceptItems: List<ContentTypeWithQuality>,
    contentType: ContentType?
): Boolean {
    if (acceptItems.isEmpty() || contentType == null) return true
    return acceptItems.any { contentType.match(it.contentType) }
}
