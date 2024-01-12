/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.jackson

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.*

/**
 * A jackson converter for the [WebSockets] plugin
 */
public class JacksonWebsocketContentConverter(
    private val objectmapper: ObjectMapper = jacksonObjectMapper()
) : WebsocketContentConverter {
    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any?): Frame {
        val convertedValue = objectmapper.writeValueAsString(value).toByteArray(charset = charset)
        return Frame.Text(true, convertedValue)
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any? {
        if (!isApplicable(content)) {
            throw WebsocketConverterNotFoundException("Unsupported frame ${content.frameType.name}")
        }
        try {
            return withContext(Dispatchers.IO) {
                val data = charset.newDecoder().decode(buildPacket { writeFully(content.readBytes()) })
                objectmapper.readValue(data, objectmapper.constructType(typeInfo.reifiedType))
            }
        } catch (cause: Exception) {
            val convertException = JsonConvertException("Illegal json parameter found: ${cause.message}", cause)

            when (cause) {
                is JsonParseException -> throw convertException
                is JsonMappingException -> throw convertException
                else -> throw cause
            }
        }
    }

    override fun isApplicable(frame: Frame): Boolean {
        return frame is Frame.Text || frame is Frame.Binary
    }
}
