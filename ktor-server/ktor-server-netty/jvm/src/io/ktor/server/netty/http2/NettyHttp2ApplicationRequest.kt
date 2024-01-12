/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http2

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.net.*
import kotlin.coroutines.*

@Suppress("DEPRECATION")
@OptIn(InternalAPI::class)
internal class NettyHttp2ApplicationRequest(
    call: PipelineCall,
    coroutineContext: CoroutineContext,
    context: ChannelHandlerContext,
    val nettyHeaders: Http2Headers,
    val contentByteChannel: ByteChannel = ByteChannel()
) : NettyApplicationRequest(
    call,
    coroutineContext,
    context,
    contentByteChannel,
    nettyHeaders[":path"]?.toString() ?: "/",
    keepAlive = true
) {

    override val engineHeaders: Headers by lazy {
        Headers.build {
            nettyHeaders.forEach { (name, value) ->
                if (name.isNotEmpty() && name[0] != ':') {
                    append(
                        name.toString(),
                        value.toString()
                    )
                }
            }
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    val contentActor = actor<Http2DataFrame>(
        Dispatchers.Unconfined,
        kotlinx.coroutines.channels.Channel.UNLIMITED
    ) {
        http2frameLoop(contentByteChannel)
    }

    override val local = Http2LocalConnectionPoint(
        nettyHeaders,
        context.channel().localAddress() as? InetSocketAddress,
        context.channel().remoteAddress() as? InetSocketAddress,
    )

    override val cookies: RequestCookies = NettyApplicationRequestCookies(this)
}
