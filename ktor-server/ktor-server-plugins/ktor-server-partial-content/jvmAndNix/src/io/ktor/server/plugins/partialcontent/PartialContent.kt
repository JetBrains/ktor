/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.partialcontent

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlin.properties.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.partialcontent.PartialContent")

/**
 * A configuration for the [PartialContent] plugin.
 */
@KtorDsl
public class PartialContentConfig {
    /**
     * Specifies a maximum number of ranges that might be accepted from an HTTP request.
     *
     * If an HTTP request specifies more ranges, they will all be merged into a single range.
     */
    public var maxRangeCount: Int by Delegates.vetoable(10) { _, _, new ->
        new > 0 || throw IllegalArgumentException("Bad maxRangeCount value $new")
    }
}

/**
 * A plugin that adds support for handling HTTP range requests used to send only a portion of an HTTP message back to a client.
 * This plugin is useful for streaming content or resuming partial downloads.
 *
 * To learn more, see [Partial content](https://ktor.io/docs/partial-content.html).
 */
public val PartialContent: RouteScopedPlugin<PartialContentConfig> = createRouteScopedPlugin(
    "PartialContent",
    ::PartialContentConfig
) {
    onCall { call ->
        if (call.request.ranges() == null) {
            LOGGER.trace("Skip ${call.request.uri}: no ranges specified")
            return@onCall
        }

        if (!call.isGetOrHead()) {
            LOGGER.trace("Skip ${call.request.uri}: not a GET or HEAD request")

            val message = HttpStatusCode.MethodNotAllowed
                .description("Method ${call.request.local.method.value} is not allowed with range request")
            if (!call.response.isCommitted) {
                call.respond(message)
            }
            return@onCall
        }

        call.suppressCompression()
    }

    on(BodyTransformedHook) { call, message ->
        val rangeSpecifier = call.request.ranges()
        if (rangeSpecifier == null) {
            LOGGER.trace("No range header specified for ${call.request.uri}")
            if (message is OutgoingContent.ReadChannelContent && message !is PartialOutgoingContent) {
                transformBodyTo(PartialOutgoingContent.Bypass(message))
            }
            return@on
        }

        if (!call.isGetOrHead()) return@on

        if (message is OutgoingContent.ReadChannelContent && message !is PartialOutgoingContent) {
            val length = message.contentLength ?: return@on
            tryProcessRange(message, call, rangeSpecifier, length, pluginConfig.maxRangeCount)
        }
    }
}
