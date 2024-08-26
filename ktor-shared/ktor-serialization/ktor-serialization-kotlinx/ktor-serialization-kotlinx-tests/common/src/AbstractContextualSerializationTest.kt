/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.serialization.kotlinx.test

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*
import kotlin.test.*

@Serializable
public data class UserData(val id: Int, val name: String)

public object UserDataSerializer : KSerializer<UserData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UserData")

    override fun deserialize(decoder: Decoder): UserData {
        val id = decoder.decodeString().toInt(16)
        val name = decoder.decodeString()
        return UserData(id, name)
    }

    override fun serialize(encoder: Encoder, value: UserData) {
        encoder.encodeString(value.id.toString(16))
        encoder.encodeString(value.name)
    }
}

public abstract class AbstractContextualSerializationTest<T : SerialFormat> {
    protected abstract val defaultContentType: ContentType
    protected abstract val defaultSerializationFormat: T
    protected abstract fun buildContextualSerializer(context: SerializersModule): T
    protected abstract fun assertEquals(
        expectedAsJson: String,
        actual: ByteArray,
        format: T,
        serializer: KSerializer<*>
    ): Boolean

    @Test
    public fun testSerializationWithContext() {
        testSuspend {
            val context = serializersModuleOf(UserData::class, UserDataSerializer)
            val contextualSerializer = buildContextualSerializer(context)
            val contextual = KotlinxSerializationConverter(contextualSerializer)
            val simple = KotlinxSerializationConverter(defaultSerializationFormat)

            val data = UserData(1, "kotlin")

            val contextualResult = contextual.testSerialize(data)
            val simpleResult = simple.testSerialize(data)

            assertEquals(""""1""kotlin"""", contextualResult, contextualSerializer, UserDataSerializer)
            assertEquals(
                """{"id":1,"name":"kotlin"}""",
                simpleResult,
                defaultSerializationFormat,
                UserData.serializer()
            )

            assertEquals(
                data,
                contextual.deserialize(Charsets.UTF_8, typeInfo<UserData>(), ByteReadChannel(contextualResult))
            )
            assertEquals(
                data,
                simple.deserialize(Charsets.UTF_8, typeInfo<UserData>(), ByteReadChannel(simpleResult))
            )
        }
    }

    private suspend inline fun <reified T : Any> ContentConverter.testSerialize(data: T): ByteArray {
        val content = serialize(defaultContentType, Charsets.UTF_8, typeInfo<T>(), data)
        return (content as? OutgoingContent.ByteArrayContent)?.bytes() ?: error("Failed to get serialized $data")
    }
}
