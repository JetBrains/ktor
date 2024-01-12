/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
internal data class User(val id: Int, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

@Serializable
data class WithList(val aList: List<String> = emptyList())

@ExperimentalSerializationApi
class ProtoBufSerializationTest {

    @Test
    fun testRegisterCustom() = testSuspend {
        val serializer = KotlinxSerializationConverter(DefaultProtoBuf)

        val user = User(1, "user1")
        val actual = serializer.testSerialize(user)

        assertContentEquals(byteArrayOf(8, 1, 18, 5, 117, 115, 101, 114, 49), actual)
    }

    @Test
    fun testEmptyList() = testSuspend {
        val serializer = KotlinxSerializationConverter(DefaultProtoBuf)

        val item = WithList()
        val serialized = serializer.testSerialize(item)
        val deserialized = serializer.deserialize(Charsets.UTF_8, typeInfo<WithList>(), ByteReadChannel(serialized))

        assertEquals(item, deserialized)
    }

    @Test
    fun testRegisterCustomList() = testSuspend {
        val serializer = KotlinxSerializationConverter(DefaultProtoBuf)

        val user = User(2, "login2")
        val photo = Photo(3, "photo3.jpg")

        assertContentEquals(
            byteArrayOf(1, 10, 8, 2, 18, 6, 108, 111, 103, 105, 110, 50),
            serializer.testSerialize(listOf(user))
        )
        assertContentEquals(
            byteArrayOf(1, 14, 8, 3, 18, 10, 112, 104, 111, 116, 111, 51, 46, 106, 112, 103),
            serializer.testSerialize(listOf(photo))
        )
    }

    private suspend inline fun <reified T : Any> ContentConverter.testSerialize(data: T): ByteArray {
        val content = serialize(ContentType.Application.ProtoBuf, Charsets.UTF_8, typeInfo<T>(), data)
        return (content as? ByteArrayContent)?.bytes() ?: error("Failed to get serialized $data")
    }
}
