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

/**
 * A test application request
 *
 * @property method HTTP method to be sent or executed
 * @property uri HTTP url to sent request to or was sent to
 * @property version HTTP version to sent or executed
 * @property port (Optional) HTTP port to send request to
 * @property protocol HTTP protocol to be used or was used
 */
public class TestApplicationRequest constructor(
    call: TestApplicationCall,
    closeRequest: Boolean,
    method: HttpMethod = HttpMethod.Get,
    uri: String = "/",
    port: Int? = null,
    version: String = "HTTP/1.1"
) : BaseApplicationRequest(call), CoroutineScope by call {

    var uri = uri
    var protocol: String = "http"
    var port = port
    var version = version
    var method = method

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
    var bodyChannel: ByteReadChannel = if (closeRequest) ByteReadChannel.Empty else ByteChannel()

    override val queryParameters: Parameters by lazy { encodeParameters(rawQueryParameters) }

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
public fun TestApplicationRequest.setBody(value: ByteReadPacket) {
    bodyChannel = ByteReadChannel(value.readBytes())
}

/**
 * Sets a multipart HTTP request body.
 */
@Suppress("DEPRECATION")
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
                            channel.writeFully(it.provider().readRemaining().readBytes())
                            ""
                        }

                        is PartData.BinaryItem -> {
                            channel.writeFully(it.provider().readBytes())
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
@Suppress("DEPRECATION")
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
                        channel.writeFully(it.provider().readRemaining().readBytes())
                        ""
                    }

                    is PartData.BinaryItem -> {
                        channel.writeFully(it.provider().readBytes())
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
