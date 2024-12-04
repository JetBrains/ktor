/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.protobuf

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

/**
 * The default protobuf configuration used in [KotlinxSerializationConverter]. The settings are:
 * - defaults are serialized
 *
 * See [ProtoBuf] for more details.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.serialization.kotlinx.protobuf.DefaultProtoBuf)
 */
@OptIn(ExperimentalSerializationApi::class)
public val DefaultProtoBuf: ProtoBuf = ProtoBuf {
    encodeDefaults = true
}

/**
 * Registers the `application/protobuf` (or another specified [contentType]) content type
 * to the [ContentNegotiation] plugin using kotlinx.serialization.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 *
 * @param protobuf format instance (optional)
 * @param contentType to register with, `application/protobuf` by default
 */
@OptIn(ExperimentalSerializationApi::class)
public fun Configuration.protobuf(
    protobuf: ProtoBuf = DefaultProtoBuf,
    contentType: ContentType = ContentType.Application.ProtoBuf
) {
    serialization(contentType, protobuf)
}
