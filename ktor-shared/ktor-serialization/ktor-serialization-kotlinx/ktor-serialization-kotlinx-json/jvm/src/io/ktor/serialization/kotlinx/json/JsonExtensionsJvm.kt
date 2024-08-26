/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.serialization.kotlinx.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
internal actual suspend fun deserializeSequence(
    format: Json,
    content: ByteReadChannel,
    typeInfo: TypeInfo
): Sequence<Any?>? =
    withContext(Dispatchers.IO) {
        val inputStream = content.toInputStream()
        // kotlinx.serialization provides optimized sequence deserialization
        // Elements are parsed lazily when the resulting Sequence is evaluated.
        // The resulting sequence is tied to the stream and can be evaluated only once.
        val elementTypeInfo = typeInfo.argumentTypeInfo()
        val serializer = format.serializersModule.serializerForTypeInfo(elementTypeInfo)
        format.decodeToSequence(inputStream, serializer)
    }
