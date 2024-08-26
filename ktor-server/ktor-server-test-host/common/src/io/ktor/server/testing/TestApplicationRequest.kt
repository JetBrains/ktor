/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.testing.internal.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*

/**
 * A test application request
 *
 * @property method HTTP method to be sent or executed
 * @property uri HTTP url to sent request to or was sent to
 * @property version HTTP version to sent or executed
 * @property port (Optional) HTTP port to send request to
 * @property protocol HTTP protocol to be used or was used
 */
public class TestApplicationRequest(
    call: TestApplicationCall,
    closeRequest: Boolean,
    public var method: HttpMethod = HttpMethod.Get,
    public var uri: String = "/",
    public var port: Int? = null,
    public var version: String = "HTTP/1.1"
) : BaseApplicationRequest(call), CoroutineScope by call {

    public var protocol: String = "http"

    override val local: RequestConnectionPoint = object : RequestConnectionPoint {
        override val uri: String
            get() = this@TestApplicationRequest.uri

        override val method: HttpMethod
            get() = this@TestApplicationRequest.method

        override val scheme: String
            get() = protocol

        @Deprecated(
            "Use localPort or serverPort instead",
            level = DeprecationLevel.ERROR
        )
        override val port: Int
            get() = this@TestApplicationRequest.port
                ?: header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt()
                ?: 80

        @Deprecated(
            "Use localHost or serverHost instead",
            level = DeprecationLevel.ERROR
        )
        override val host: String
            get() = header(HttpHeaders.Host)?.substringBefore(":") ?: "localhost"

        override val localPort: Int
            get() = this@TestApplicationRequest.port ?: 80
        override val serverPort: Int
            get() = header(HttpHeaders.Host)?.substringAfter(":", "80")?.toInt() ?: localPort

        override val localHost: String
            get() = "localhost"
        override val serverHost: String
            get() = header(HttpHeaders.Host)?.substringBefore(":") ?: localHost
        override val localAddress: String
            get() = "localhost"

        override val remoteHost: String
            get() = "localhost"
        override val remotePort: Int
            get() = 0
        override val remoteAddress: String
            get() = "localhost"

        override val version: String
            get() = this@TestApplicationRequest.version

        override fun toString(): String =
            "TestConnectionPoint(uri=$uri, method=$method, version=$version, localAddress=$localAddress, " +
                "localPort=$localPort, remoteAddress=$remoteAddress, remotePort=$remotePort)"
    }

    /**
     * Request body channel.
     */
    public var bodyChannel: ByteReadChannel = if (closeRequest) ByteReadChannel.Empty else ByteChannel()

    override val queryParameters: Parameters by lazy { encodeParameters(rawQueryParameters).toQueryParameters() }

    override val rawQueryParameters: Parameters by lazy {
        parseQueryString(queryString(), decode = false)
    }

    override val cookies: RequestCookies = RequestCookies(this)

    private var headersMap: MutableMap<String, MutableList<String>>? = mutableMapOf()

    /**
     * Adds an HTTP request header.
     */
    public fun addHeader(name: String, value: String) {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        map.getOrPut(name) { mutableListOf() }.add(value)
    }

    override val engineHeaders: Headers by lazy {
        val map = headersMap ?: throw Exception("Headers were already acquired for this request")
        headersMap = null
        Headers.build {
            map.forEach { (name, values) ->
                appendAll(name, values)
            }
        }
    }

    override val engineReceiveChannel: ByteReadChannel get() = bodyChannel
}

/**
 * Converts parameters to query parameters by fixing the [Parameters.get] method
 * to make it return an empty string for the query parameter without value
 */
private fun Parameters.toQueryParameters(): Parameters {
    val parameters = this
    return object : Parameters {
        override fun get(name: String): String? {
            val values = getAll(name) ?: return null
            return if (values.isEmpty()) "" else values.first()
        }
        override val caseInsensitiveName: Boolean
            get() = parameters.caseInsensitiveName
        override fun getAll(name: String): List<String>? = parameters.getAll(name)
        override fun names(): Set<String> = parameters.names()
        override fun entries(): Set<Map.Entry<String, List<String>>> = parameters.entries()
        override fun isEmpty(): Boolean = parameters.isEmpty()
    }
}

/**
 * Sets an HTTP request body text content.
 */
public fun TestApplicationRequest.setBody(value: String) {
    setBody(value.toByteArray())
}

/**
 * Sets an HTTP request body bytes.
 */
public fun TestApplicationRequest.setBody(value: ByteArray) {
    bodyChannel = ByteReadChannel(value)
}

/**
 * Set HTTP request body from [ByteReadPacket]
 */

public fun TestApplicationRequest.setBody(value: Source) {
    bodyChannel = ByteReadChannel(value.readByteArray())
}

/**
 * Sets a multipart HTTP request body.
 */

public fun TestApplicationRequest.setBody(boundary: String, parts: List<PartData>) {
    bodyChannel = writer(Dispatchers.IOBridge) {
        if (parts.isEmpty()) return@writer

        try {
            append("\r\n\r\n")
            parts.forEach {
                append("--$boundary\r\n")
                for ((key, values) in it.headers.entries()) {
                    append("$key: ${values.joinToString(";")}\r\n")
                }
                append("\r\n")
                append(
                    when (it) {
                        is PartData.FileItem -> {
                            channel.writeFully(it.provider().readRemaining().readByteArray())
                            ""
                        }

                        is PartData.BinaryItem -> {
                            channel.writeFully(it.provider().readByteArray())
                            ""
                        }

                        is PartData.FormItem -> it.value
                        is PartData.BinaryChannelItem -> {
                            it.provider().copyTo(channel)
                            ""
                        }
                    }
                )
                append("\r\n")
            }

            append("--$boundary--\r\n")
        } finally {
            parts.forEach { it.dispose() }
        }
    }.channel
}

/**
 * Sets a multipart HTTP request body.
 */

@OptIn(DelicateCoroutinesApi::class)
internal fun buildMultipart(
    boundary: String,
    parts: List<PartData>
): ByteReadChannel = GlobalScope.writer(Dispatchers.IOBridge) {
    if (parts.isEmpty()) return@writer

    try {
        append("\r\n\r\n")
        parts.forEach {
            append("--$boundary\r\n")
            for ((key, values) in it.headers.entries()) {
                append("$key: ${values.joinToString(";")}\r\n")
            }
            append("\r\n")
            append(
                when (it) {
                    is PartData.FileItem -> {
                        channel.writeFully(it.provider().readRemaining().readByteArray())
                        ""
                    }

                    is PartData.BinaryItem -> {
                        channel.writeFully(it.provider().readByteArray())
                        ""
                    }

                    is PartData.FormItem -> it.value
                    is PartData.BinaryChannelItem -> {
                        it.provider().copyTo(channel)
                        ""
                    }
                }
            )
            append("\r\n")
        }

        append("--$boundary--\r\n")
    } finally {
        parts.forEach { it.dispose() }
    }
}.channel

private suspend fun WriterScope.append(str: String, charset: Charset = Charsets.UTF_8) {
    channel.writeFully(str.toByteArray(charset))
}
