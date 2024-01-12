/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

typealias ContentConverterSerialize = suspend (ContentType, Charset, TypeInfo, Any?) -> OutgoingContent?
typealias ContentConverterDeserialize = suspend (Charset, TypeInfo, ByteReadChannel) -> Any?

class TestContentConverter(
    var serializeFn: ContentConverterSerialize = { _, _, _, _ -> null },
    var deserializeFn: ContentConverterDeserialize = { _, _, _ -> null },
) : ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? = serializeFn(contentType, charset, typeInfo, value)

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? =
        deserializeFn(charset, typeInfo, content)
}
