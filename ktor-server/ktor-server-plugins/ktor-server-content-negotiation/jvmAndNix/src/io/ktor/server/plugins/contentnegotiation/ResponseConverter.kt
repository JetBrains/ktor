/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.charsets.*

private val NOT_ACCEPTABLE = HttpStatusCodeContent(HttpStatusCode.NotAcceptable)

internal fun PluginBuilder<ContentNegotiationConfig>.convertResponseBody() = onCallRespond { call, subject ->
    if (subject is OutgoingContent) {
        LOGGER.trace("Skipping because body is already converted.")
        return@onCallRespond
    }

    if (pluginConfig.ignoredTypes.any { it.isInstance(subject) }) {
        val sourceClass = subject::class.simpleName
        val requestInfo = "${call.request.httpMethod.value} ${call.request.uri}"

        LOGGER.trace(
            "Skipping response body transformation from $sourceClass to OutgoingContent for the $requestInfo request" +
                " because the $sourceClass type is ignored. See [ContentNegotiationConfig::ignoreType]."
        )
        return@onCallRespond
    }

    val responseType = call.response.responseType ?: return@onCallRespond
    val registrations = pluginConfig.registrations
    val checkAcceptHeader = pluginConfig.checkAcceptHeaderCompliance

    transformBody {
        val acceptHeader = call.parseAcceptHeader()

        val acceptItems: List<ContentTypeWithQuality> = this@onCallRespond.pluginConfig
            .acceptContributors
            .fold(acceptHeader) { result, contributor -> contributor(call, result) }
            .distinct()
            .sortedByQuality()

        val suitableConverters = if (acceptItems.isEmpty()) {
            // all converters are suitable since client didn't indicate what it wants
            registrations
        } else {
            // select converters that match specified Accept header, in order of quality
            acceptItems.flatMap { (contentType, _) ->
                registrations.filter { it.contentType.match(contentType) }
            }.distinct()
        }

        val acceptCharset = call.request.headers.suitableCharsetOrNull()

        // Pick the first one that can convert the subject successfully
        for (registration in suitableConverters) {
            val contentType = acceptCharset?.let { charset ->
                registration.contentType.withCharset(charset)
            }

            val result = registration.converter.serialize(
                contentType = contentType ?: registration.contentType,
                charset = acceptCharset ?: Charsets.UTF_8,
                typeInfo = responseType,
                value = subject.takeIf { it != NullBody }
            )

            if (result == null) {
                LOGGER.trace("Can't convert body $subject with ${registration.converter}")
                continue
            }

            val transformedContent = transformDefaultContent(call, result)
            if (transformedContent == null) {
                LOGGER.trace("Can't convert body $subject with ${registration.converter}")
                continue
            }

            if (checkAcceptHeader && !checkAcceptHeader(acceptItems, transformedContent.contentType)) {
                LOGGER.trace(
                    "Can't send content with ${transformedContent.contentType} to client " +
                        "because it is not acceptable"
                )
                return@transformBody NOT_ACCEPTABLE
            }

            return@transformBody transformedContent
        }

        LOGGER.trace(
            "No suitable content converter found for response type ${responseType.type}" +
                " and body $subject"
        )
        return@transformBody subject
    }
}

/**
 * Returns a list of content types sorted by the quality, number of asterisks, and number of parameters.
 * @see parseAndSortContentTypeHeader
 */
private fun List<ContentTypeWithQuality>.sortedByQuality(): List<ContentTypeWithQuality> = sortedWith(
    compareByDescending<ContentTypeWithQuality> { it.quality }.thenBy {
        val contentType = it.contentType
        var asterisks = 0
        if (contentType.contentType == "*") {
            asterisks += 2
        }
        if (contentType.contentSubtype == "*") {
            asterisks++
        }
        asterisks
    }.thenByDescending { it.contentType.parameters.size }
)
