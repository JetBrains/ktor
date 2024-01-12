/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.server.request.*
import io.netty.handler.codec.http.cookie.*

internal class NettyApplicationRequestCookies(request: PipelineRequest) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        val cookieHeaders = request.headers.getAll("Cookie") ?: return emptyMap()
        val map = HashMap<String, String>(cookieHeaders.size)
        for (cookieHeader in cookieHeaders) {
            ServerCookieDecoder.LAX.decode(cookieHeader).associateByTo(map, { it.name() }, { it.value() })
        }
        return map
    }
}
