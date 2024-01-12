/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.gson

import com.google.gson.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

/**
 * GSON converter for the [WebSockets] plugin
 */
public class GsonWebsocketContentConverter(private val gson: Gson = Gson()) : WebsocketContentConverter {
    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any?): Frame {
        return Frame.Text(gson.toJson(value))
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any? {
        if (!isApplicable(content)) {
            throw WebsocketConverterNotFoundException("Unsupported frame ${content.frameType.name}")
        }
        if (gson.isExcluded(typeInfo.type)) {
            throw ExcludedTypeGsonException(typeInfo.type)
        }

        try {
            return withContext(Dispatchers.IO) {
                val reader = content.readBytes().inputStream().reader(charset)
                gson.fromJson(reader, typeInfo.reifiedType)
            }
        } catch (cause: JsonSyntaxException) {
            throw JsonConvertException("Illegal json parameter found: ${cause.message}", cause)
        }
    }

    override fun isApplicable(frame: Frame): Boolean {
        return frame is Frame.Text
    }
}
