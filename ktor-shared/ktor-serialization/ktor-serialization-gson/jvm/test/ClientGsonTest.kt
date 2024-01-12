/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.gson.*
import kotlin.test.*

class ClientGsonTest : AbstractClientContentNegotiationTest() {
    private val converter = GsonConverter()

    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")
    override val webSocketsConverter: WebsocketContentConverter = GsonWebsocketContentConverter()

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, converter)
    }

    @Test
    @Ignore
    override fun testSealed() {
    }

    @Test
    @Ignore
    override fun testSerializeFailureHasOriginalCauseMessage() {
    }
}
