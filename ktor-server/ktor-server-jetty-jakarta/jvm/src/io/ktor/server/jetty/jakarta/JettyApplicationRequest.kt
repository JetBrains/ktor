/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*

@InternalAPI
public class JettyApplicationRequest(
    call: PipelineCall,
    request: Request
) : BaseApplicationRequest(call) {

    override val cookies: RequestCookies = JettyRequestCookies(this, request)

    override val engineHeaders: Headers = JettyHeaders(request)

    override val engineReceiveChannel: ByteReadChannel = Content.Source.asInputStream(request).toByteReadChannel()

    override val local: RequestConnectionPoint = JettyConnectionPoint(request)

    override val queryParameters: Parameters by lazy { encodeParameters(rawQueryParameters) }

    override val rawQueryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        val uri = request.httpURI.query ?: return@lazy Parameters.Empty
        parseQueryString(uri, decode = false)
    }
}

@InternalAPI
public class JettyRequestCookies(
    request: JettyApplicationRequest,
    private val jettyRequest: Request
) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        return Request.getCookies(jettyRequest).associate { it.name to it.value }
    }
}

@InternalAPI
public class JettyHeaders(
    private val jettyRequest: Request
) : Headers {
    override val caseInsensitiveName: Boolean = true

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return jettyRequest.headers.fieldNamesCollection.map {
            object : Map.Entry<String, List<String>> {
                override val key: String = it
                override val value: List<String> = jettyRequest.headers.getValuesList(it)
            }
        }.toSet()
    }

    override fun getAll(name: String): List<String>? = jettyRequest.headers.getValuesList(name).takeIf { it.isNotEmpty() }

    override fun get(name: String): String? = jettyRequest.headers.get(name).takeIf { it.isNotEmpty() }

    override fun isEmpty(): Boolean = jettyRequest.headers.size() == 0

    override fun names(): Set<String> = jettyRequest.headers.fieldNamesCollection
}

@InternalAPI
public class JettyConnectionPoint(
    request: Request
) : RequestConnectionPoint {
    @Deprecated("Use localHost or serverHost instead")
    override val host: String = request.httpURI.host

    override val localAddress: String = Request.getLocalAddr(request)

    override val localHost: String = Request.getServerName(request)

    override val localPort: Int = Request.getLocalPort(request)

    override val method: HttpMethod = HttpMethod.parse(request.method)

    @Deprecated("Use localPort or serverPort instead")
    override val port: Int = request.httpURI.port

    override val remoteAddress: String = Request.getRemoteAddr(request)

    override val remoteHost: String = Request.getServerName(request)

    override val remotePort: Int = Request.getRemotePort(request)

    override val scheme: String = request.httpURI.scheme

    override val serverHost: String = Request.getServerName(request)

    override val serverPort: Int = Request.getServerPort(request)

    override val uri: String = request.httpURI.pathQuery

    override val version: String = request.connectionMetaData.httpVersion.asString()
}
