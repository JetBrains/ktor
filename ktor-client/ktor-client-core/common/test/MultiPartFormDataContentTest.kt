import io.ktor.client.request.forms.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

class MultiPartFormDataContentTest {

    @OptIn(InternalAPI::class)
    @Test
    fun testMultiPartFormDataContentHasCorrectPrefix() = testSuspend {
        val formData = MultiPartFormDataContent(
            formData {
                append("Hello", "World")
            }
        )

        val channel = ByteChannel()
        formData.writeTo(channel)
        channel.close()

        val actual = channel.readRemaining().readByteArray()

        assertNotEquals('\r'.code.toByte(), actual[0])
        assertNotEquals('\n'.code.toByte(), actual[1])
        assertNotEquals('\r'.code.toByte(), actual[2])
        assertNotEquals('\n'.code.toByte(), actual[3])
    }

    @Test
    fun testEmptyByteReadChannel() = testSuspend {
        val data = MultiPartFormDataContent(
            formData {
                append("channel", ChannelProvider { ByteReadChannel.Empty })
            },
            boundary = "boundary"
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=channel",
                "",
                "",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testByteReadChannelWithString() = testSuspend {
        val content = "body"
        val data = MultiPartFormDataContent(
            formData {
                append("channel", ChannelProvider(size = content.length.toLong()) { ByteReadChannel(content) })
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=channel",
                "Content-Length: 4",
                "",
                "body",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testNumberQuoted() = testSuspend {
        val data = MultiPartFormDataContent(
            formData {
                append("not_a_forty_two", 1337)
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=not_a_forty_two",
                "Content-Length: 4",
                "",
                "1337", // note quotes
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testBooleanQuoted() = testSuspend {
        val data = MultiPartFormDataContent(
            formData {
                append("is_forty_two", false)
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=is_forty_two",
                "Content-Length: 5",
                "",
                "false", // note quotes
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testStringsList() = testSuspend {
        val data = MultiPartFormDataContent(
            formData {
                append("platforms[]", listOf("windows", "linux", "osx"))
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 7",
                "",
                "windows",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 5",
                "",
                "linux",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 3",
                "",
                "osx",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testStringsArray() = testSuspend {
        val data = MultiPartFormDataContent(
            formData {
                append("platforms[]", arrayOf("windows", "linux", "osx"))
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 7",
                "",
                "windows",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 5",
                "",
                "linux",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 3",
                "",
                "osx",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testStringsListBadKey() = testSuspend {
        val attempt = {
            MultiPartFormDataContent(
                formData {
                    append("bad_key", listOf("whatever", "values"))
                },
                boundary = "boundary",
            )
        }

        val ex = assertFails { attempt() }
        assertEquals(ex.message, "Array parameter must be suffixed with square brackets ie `bad_key[]`")
    }

    @Test
    fun testByteReadChannelOverBufferSize() = testSuspend {
        val body = ByteArray(4089) { 'k'.code.toByte() }
        val data = MultiPartFormDataContent(
            formData {
                append("channel", ChannelProvider { ByteReadChannel(body) })
            },
            boundary = "boundary"
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=channel",
                "",
                "k".repeat(4089),
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    private suspend fun MultiPartFormDataContent.readString(charset: Charset = Charsets.UTF_8): String {
        val bytes = readBytes()
        return bytes.decodeToString(0, 0 + bytes.size)
    }

    @OptIn(InternalAPI::class)
    private suspend fun MultiPartFormDataContent.readBytes(): ByteArray = coroutineScope {
        val channel = ByteChannel()
        val writeJob = launch {
            writeTo(channel)
            channel.close()
        }

        val result = channel.readRemaining().readByteArray()
        writeJob.join()

        result
    }
}
