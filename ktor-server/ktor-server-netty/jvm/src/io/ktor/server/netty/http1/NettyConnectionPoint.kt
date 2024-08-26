/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.netty.channel.*
import io.netty.handler.codec.http.*
import java.net.*

private const val DEFAULT_PORT = 80

internal class NettyConnectionPoint(
    private val request: HttpRequest,
    private val context: ChannelHandlerContext,
) : RequestConnectionPoint {

    override val version: String
        get() = request.protocolVersion().text()

    override val uri: String
        get() = request.uri()

    override val method: HttpMethod
        get() = HttpMethod.parse(request.method().name())

    override val scheme by lazy { if (context.pipeline().context("ssl") == null) "http" else "https" }

    @Deprecated(
        "Use localHost or serverHost instead",
        level = DeprecationLevel.ERROR
    )
    override val host: String
        get() = request.headers().get(HttpHeaders.Host)?.substringBefore(":") ?: (
            context.channel()
                .localAddress() as? InetSocketAddress
            )?.let { it.hostName ?: it.address.hostAddress } ?: "localhost"

    @Deprecated(
        "Use localPort or serverPort instead",
        level = DeprecationLevel.ERROR
    )
    override val port: Int
        get() = (context.channel().localAddress() as? InetSocketAddress)?.port ?: DEFAULT_PORT

    override val localHost: String
        get() = (context.channel().localAddress() as? InetSocketAddress)?.let { it.hostName ?: it.hostString }
            ?: "localhost"

    override val serverHost: String
        get() = request.headers().get(HttpHeaders.Host)?.substringBeforeLast(":") ?: localHost

    override val localAddress: String
        get() = (context.channel().localAddress() as? InetSocketAddress)?.hostString ?: "localhost"

    private val defaultPort
        get() = URLProtocol.createOrDefault(scheme).defaultPort

    override val localPort: Int
        get() = (context.channel().localAddress() as? InetSocketAddress)?.port ?: defaultPort

    override val serverPort: Int
        get() = request.headers().get(HttpHeaders.Host)?.substringAfterLast(":", defaultPort.toString())?.toInt()
            ?: localPort

    override val remoteHost: String
        get() = (context.channel().remoteAddress() as? InetSocketAddress)?.let {
            it.hostName ?: it.address.hostAddress
        } ?: "unknown"

    override val remotePort: Int
        get() = (context.channel().remoteAddress() as? InetSocketAddress)?.port ?: 0
    override val remoteAddress: String
        get() = (context.channel().remoteAddress() as? InetSocketAddress)?.hostString ?: "unknown"

    override fun toString(): String =
        "NettyConnectionPoint(" +
            "uri=$uri, method=$method, version=$version, localAddress=$localAddress, localPort=$localPort, " +
            "remoteAddress=$remoteAddress, remotePort=$remotePort)"
}
