/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import java.io.*
import kotlin.coroutines.*
import kotlin.test.*

class ExceptionsTest {

    @Test
    fun testResponseExceptionSerializable() {
        val exception = createResponseException()

        val serialized = serialize(exception)
        val deserialized = deserialize(serialized)

        deserialized as ResponseException
    }

    private fun serialize(obj: Any): ByteArray {
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(obj)
        return baos.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): Any? {
        val bais = ByteArrayInputStream(bytes)
        val ois = ObjectInputStream(bais)
        return ois.readObject()
    }
}

@OptIn(InternalAPI::class)
private fun createResponseException(): ResponseException = ResponseException(
    object : HttpResponse() {
        override val call: HttpClientCall
            get() = TODO("Not yet implemented")
        override val status: HttpStatusCode
            get() = TODO("Not yet implemented")
        override val version: HttpProtocolVersion
            get() = TODO("Not yet implemented")
        override val requestTime: GMTDate
            get() = TODO("Not yet implemented")
        override val responseTime: GMTDate
            get() = TODO("Not yet implemented")
        override val rawContent: ByteReadChannel
            get() = TODO("Not yet implemented")
        override val headers: Headers
            get() = TODO("Not yet implemented")
        override val coroutineContext: CoroutineContext
            get() = TODO("Not yet implemented")

        override fun toString(): String = "FakeCall"
    },
    cachedResponseText = "Fake text"
)
