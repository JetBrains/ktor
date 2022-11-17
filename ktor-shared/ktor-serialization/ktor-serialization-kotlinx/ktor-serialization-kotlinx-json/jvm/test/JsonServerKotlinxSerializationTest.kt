/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.serialization.kotlinx.test.json

import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.*
import java.nio.charset.*
import kotlin.test.*

class JsonServerKotlinxSerializationTest : AbstractServerSerializationKotlinxTest() {
    private val prettyPrintJson = Json(DefaultJson) {
        prettyPrint = true
    }
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiationConfig.configureContentNegotiation(
        contentType: ContentType,
        streamRequestBody: Boolean,
        prettyPrint: Boolean
    ) {
        register(contentType, KotlinxSerializationConverter(json(prettyPrint)))
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultJson.decodeFromString(serializer, String(t))
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset, prettyPrint: Boolean): List<TestEntity> {
        val jsonString = String(t, charset)
        val deserialized = json(prettyPrint).decodeFromString(listSerializer, String(t, charset))
        val pretty = json(prettyPrint).encodeToString(listSerializer, deserialized)
        assertEquals(pretty, jsonString)
        return deserialized
    }

    private fun json(prettyPrint: Boolean) =
        if (prettyPrint) {
            prettyPrintJson
        } else {
            DefaultJson
        }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultJson.encodeToString(serializer, any).toByteArray()
    }
}
