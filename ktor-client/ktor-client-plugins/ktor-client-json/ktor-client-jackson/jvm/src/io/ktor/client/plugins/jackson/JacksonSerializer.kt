/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package io.ktor.client.plugins.jackson

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.client.plugins.json.JsonSerializer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*

@Deprecated(
    "Please use ContentNegotiation plugin and its converters: https://ktor.io/docs/migrating-2.html#serialization-client", // ktlint-disable max-line-length
    level = DeprecationLevel.ERROR
)
public class JacksonSerializer(
    jackson: ObjectMapper = jacksonObjectMapper(),
    block: ObjectMapper.() -> Unit = {}
) : JsonSerializer {
    private val backend = jackson.apply(block)

    override fun write(data: Any, contentType: ContentType): OutgoingContent =
        TextContent(backend.writeValueAsString(data), contentType)

    override fun read(type: TypeInfo, body: Input): Any {
        return backend.readValue(body.readText(), backend.typeFactory.constructType(type.reifiedType))
    }
}
