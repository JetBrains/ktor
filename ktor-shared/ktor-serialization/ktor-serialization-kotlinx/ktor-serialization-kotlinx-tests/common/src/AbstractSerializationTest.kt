/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
internal data class User(val id: Long, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

@Serializable
public data class GithubProfile(
    val login: String,
    val id: Int,
    val name: String
)

public abstract class AbstractSerializationTest<T : SerialFormat> {
    protected abstract val defaultContentType: ContentType
    protected abstract val defaultSerializationFormat: T
    protected abstract fun assertEquals(
        expectedAsJson: String,
        actual: ByteArray,
        format: T,
    ): Boolean

    @Test
    public fun testMapsElements() {
        testSuspend {
            val testSerializer = KotlinxSerializationConverter(defaultSerializationFormat)
            testSerializer.testSerialize(
                mapOf(
                    "a" to "1",
                    "b" to "2"
                )
            ).let { result ->
                assertEquals("""{"a":"1","b":"2"}""", result, defaultSerializationFormat)
            }

            testSerializer.testSerialize(
                mapOf(
                    "a" to "1",
                    "b" to null
                )
            ).let { result ->
                assertEquals("""{"a":"1","b":null}""", result, defaultSerializationFormat)
            }

            testSerializer.testSerialize(
                mapOf(
                    "a" to "1",
                    null to "2"
                )
            ).let { result ->
                assertEquals("""{"a":"1",null:"2"}""", result, defaultSerializationFormat)
            }

            // this is not yet supported
            assertFails {
                testSerializer.testSerialize<Map<String, Any>>(
                    mapOf(
                        "a" to "1",
                        "b" to 2
                    )
                )
            }
        }
    }

    @Test
    public fun testRegisterCustom() {
        testSuspend {
            val serializer = KotlinxSerializationConverter(defaultSerializationFormat)

            val user = User(1, "vasya")
            val actual = serializer.testSerialize(user)
            assertEquals("""{"id":1,"login":"vasya"}""", actual, defaultSerializationFormat)
        }
    }

    @Test
    public fun testRegisterCustomList() {
        testSuspend {
            val serializer = KotlinxSerializationConverter(defaultSerializationFormat)

            val user = User(2, "petya")
            val photo = Photo(3, "petya.jpg")

            assertEquals(
                """[{"id":2,"login":"petya"}]""",
                serializer.testSerialize(listOf(user)),
                defaultSerializationFormat
            )
            assertEquals(
                """[{"id":3,"path":"petya.jpg"}]""",
                serializer.testSerialize(listOf(photo)),
                defaultSerializationFormat
            )
        }
    }

    @Test
    public open fun testRegisterCustomFlow() {
        testSuspend {
            val serializer = KotlinxSerializationConverter(defaultSerializationFormat)

            val user = User(2, "petya")
            val photo = Photo(3, "petya.jpg")

            assertEquals(
                """[{"id":2,"login":"petya"}]""",
                serializer.testSerialize(flowOf(user)),
                defaultSerializationFormat
            )
            assertEquals(
                """[{"id":3,"path":"petya.jpg"}]""",
                serializer.testSerialize(flowOf(photo)),
                defaultSerializationFormat
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class, InternalAPI::class)
    protected suspend inline fun <reified T : Any> ContentConverter.testSerialize(data: T): ByteArray {
        return when (
            val content =
                serialize(defaultContentType, Charsets.UTF_8, typeInfo<T>(), data)
        ) {
            is OutgoingContent.ByteArrayContent -> content.bytes()
            is ChannelWriterContent -> {
                val channel = ByteChannel()
                GlobalScope.launch {
                    content.writeTo(channel)
                    channel.close()
                }
                channel.readRemaining().readByteArray()
            }

            else -> error("Failed to get serialized $data")
        }
    }
}
