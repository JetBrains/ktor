/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.json

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*

/**
 * Client json serializer.
 */
@Deprecated(
    "Please use ContentNegotiation plugin and its converters: https://ktor.io/docs/migrating-2.html#serialization-client", // ktlint-disable max-line-length
    level = DeprecationLevel.ERROR
)
public interface JsonSerializer {
    /**
     * Convert data object to [OutgoingContent].
     */
    public fun write(data: Any, contentType: ContentType): OutgoingContent

    /**
     * Convert data object to [OutgoingContent].
     */
    public fun write(data: Any): OutgoingContent = write(data, ContentType.Application.Json)

    /**
     * Read content from response using information specified in [type].
     */

    public fun read(type: TypeInfo, body: Input): Any
}
