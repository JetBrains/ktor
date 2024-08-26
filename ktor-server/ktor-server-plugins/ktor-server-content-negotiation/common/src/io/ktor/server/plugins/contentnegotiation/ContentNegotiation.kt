/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.contentnegotiation.ContentNegotiation")

/**
 * A functional type for accepted content types contributor.
 * @see ContentNegotiation.Configuration.accept
 */
public typealias AcceptHeaderContributor = (
    call: ApplicationCall,
    acceptedContentTypes: List<ContentTypeWithQuality>
) -> List<ContentTypeWithQuality>

/**
 * A pair of [ContentType] and [quality] usually parsed from the [HttpHeaders.Accept] headers.
 * @param contentType
 * @param quality
 */
public data class ContentTypeWithQuality(val contentType: ContentType, val quality: Double = 1.0) {
    init {
        require(quality in 0.0..1.0) { "Quality should be in range [0, 1]: $quality" }
    }
}

/**
 * A plugin that serves two primary purposes:
 * - Negotiating media types between the client and server. For this, it uses the `Accept` and `Content-Type` headers.
 * - Serializing/deserializing the content in a specific format.
 *    Ktor supports the following formats out-of-the-box: `JSON`, `XML`, `CBOR` and `ProtoBuf`.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 */
public val ContentNegotiation: RouteScopedPlugin<ContentNegotiationConfig> = createRouteScopedPlugin(
    "ContentNegotiation",
    ::ContentNegotiationConfig
) {
    convertRequestBody()
    convertResponseBody()
}

/**
 * Detects a suitable charset for an application call by using the `Accept` header or fallbacks to [defaultCharset].
 */
public fun ApplicationCall.suitableCharset(defaultCharset: Charset = Charsets.UTF_8): Charset {
    for ((charset, _) in request.acceptCharsetItems()) when {
        charset == "*" -> return defaultCharset
        Charsets.isSupported(charset) -> return Charsets.forName(charset)
    }
    return defaultCharset
}
