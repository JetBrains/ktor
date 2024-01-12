/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet

import io.ktor.http.*
import javax.servlet.http.*

internal class ServletConnectionPoint(private val servletRequest: HttpServletRequest) : RequestConnectionPoint {

    override val uri = servletRequest.queryString.let { query ->
        if (query == null) {
            servletRequest.requestURI!!
        } else {
            "${servletRequest.requestURI}?$query"
        }
    }

    override val version: String = servletRequest.protocol

    override val method: HttpMethod = HttpMethod.parse(servletRequest.method)

    override val scheme: String = servletRequest.scheme ?: "http"

    @Deprecated(
        "Use localPort or serverPort instead",
        level = DeprecationLevel.ERROR
    )
    override val port: Int
        get() = servletRequest.serverPort

    @Deprecated(
        "Use localHost or serverHost instead",
        level = DeprecationLevel.ERROR
    )
    override val host: String
        get() = servletRequest.serverName ?: "localhost"

    override val localPort: Int
        get() = servletRequest.localPort
    override val serverPort: Int
        get() = servletRequest.serverPort

    override val localHost: String
        get() = servletRequest.localName ?: "localhost"
    override val serverHost: String
        get() = servletRequest.serverName ?: "localhost"
    override val localAddress: String
        get() = servletRequest.localAddr ?: "localhost"

    override val remoteHost: String
        get() = servletRequest.remoteHost
    override val remotePort: Int
        get() = servletRequest.remotePort
    override val remoteAddress: String
        get() = servletRequest.remoteAddr

    override fun toString(): String =
        "ServletConnectionPoint(uri=$uri, method=$method, version=$version, localAddress=$localAddress, " +
            "localPort=$localPort, remoteAddress=$remoteAddress, remotePort=$remotePort)"
}
