/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.serialization.kotlinx.test.json

import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.*

class JsonClientKotlinxSerializationJsonJvmTest : AbstractClientContentNegotiationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")
    override val webSocketsConverter: WebsocketContentConverter = KotlinxWebsocketSerializationConverter(DefaultJson)

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        json(contentType = contentType) // = KotlinxSerializationJsonJvmConverter
    }

    @Test
    @Ignore // https://github.com/Kotlin/kotlinx.serialization/issues/2218
    fun testSequence(): Unit = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val result = client.post {
                url(path = "/echo", port = serverPort)
                contentType(defaultContentType)
                setBody(users)
            }.body<Sequence<User>>()

            assertContentEquals(
                users.asSequence(),
                result
            )
        }
    }
}
