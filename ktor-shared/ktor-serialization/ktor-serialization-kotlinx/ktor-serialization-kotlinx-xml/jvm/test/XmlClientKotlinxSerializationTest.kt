/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.xml.*
import kotlin.test.*

class XmlClientKotlinxSerializationTest : AbstractClientContentNegotiationTest() {
    private val converter = KotlinxSerializationConverter(DefaultXml)
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("text/xml")
    override val webSocketsConverter: WebsocketContentConverter = KotlinxWebsocketSerializationConverter(DefaultXml)

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, converter)
    }

    @Test
    @Ignore
    override fun testGeneric() {
    }

    @Test
    @Ignore
    override fun testSealed() {
    }

    @Test
    @Ignore
    override fun testSerializeNested() {
    }

    @Test
    @Ignore
    override fun testSerializeNull() {
    }

    @Test
    @Ignore
    override fun testSerializeFailureHasOriginalCauseMessage() {
    }
}
