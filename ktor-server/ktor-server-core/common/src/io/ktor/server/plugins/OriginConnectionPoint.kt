/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * Represents request and connection parameters possibly overridden via https headers.
 * By default, it fallbacks to [ApplicationRequest.local]
 */

public val ApplicationRequest.origin: RequestConnectionPoint
    get() = call.attributes.getOrNull(MutableOriginConnectionPointKey) ?: local

/**
 * A key to install a mutable [RequestConnectionPoint]
 */
@Deprecated("This API will be redesigned as per https://youtrack.jetbrains.com/issue/KTOR-2657")
public val MutableOriginConnectionPointKey: AttributeKey<MutableOriginConnectionPoint> =
    AttributeKey("MutableOriginConnectionPointKey")

/**
 * Represents a [RequestConnectionPoint]. Its every component is mutable so application plugins could modify them.
 * By default, all the properties are equal to [ApplicationRequest.local] with [RequestConnectionPoint.serverHost]
 * and [RequestConnectionPoint.serverPort] overridden by [HttpHeaders.Host] header value.
 * Users can assign new values parsed from [HttpHeaders.Forwarded], [HttpHeaders.XForwardedHost], etc.
 * See [XForwardedHeaders] and [ForwardedHeaders].
 */
public class MutableOriginConnectionPoint internal constructor(
    delegate: RequestConnectionPoint
) : RequestConnectionPoint {

    override var version: String by AssignableWithDelegate { delegate.version }
    override var uri: String by AssignableWithDelegate { delegate.uri }
    override var method: HttpMethod by AssignableWithDelegate { delegate.method }
    override var scheme: String by AssignableWithDelegate { delegate.scheme }

    @Suppress("DEPRECATION_ERROR")
    @Deprecated("Use localHost or serverHost instead", level = DeprecationLevel.ERROR)
    override var host: String by AssignableWithDelegate { delegate.host }
    override var localHost: String by AssignableWithDelegate { delegate.localHost }
    override var serverHost: String by AssignableWithDelegate { delegate.serverHost }
    override var localAddress: String by AssignableWithDelegate { delegate.localAddress }

    @Suppress("DEPRECATION_ERROR")
    @Deprecated("Use localPort or serverPort instead", level = DeprecationLevel.ERROR)
    override var port: Int by AssignableWithDelegate { delegate.port }
    override var localPort: Int by AssignableWithDelegate { delegate.localPort }
    override var serverPort: Int by AssignableWithDelegate { delegate.serverPort }
    override var remoteHost: String by AssignableWithDelegate { delegate.remoteHost }
    override var remotePort: Int by AssignableWithDelegate { delegate.remotePort }
    override var remoteAddress: String by AssignableWithDelegate { delegate.remoteAddress }
}

private class AssignableWithDelegate<T : Any>(val property: () -> T) {
    private var assigned: T? = null

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any, property: KProperty<*>): T = assigned ?: property()

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        assigned = value
    }
}

internal class OriginConnectionPoint(
    private val local: RequestConnectionPoint,
    private val hostHeaderValue: String?
) : RequestConnectionPoint {
    constructor(call: ApplicationCall) : this(call.request.local, call.request.header(HttpHeaders.Host))

    override val scheme: String
        get() = local.scheme

    override val version: String
        get() = local.version

    @Suppress("DEPRECATION_ERROR")
    @Deprecated("Use localHost or serverHost instead")
    override val port: Int
        get() = hostHeaderValue?.substringAfter(":", "80")
            ?.toIntOrNull()
            ?: local.port

    override val localPort: Int
        get() = local.localPort
    override val serverPort: Int
        get() = local.serverPort

    @Suppress("DEPRECATION_ERROR")
    @Deprecated("Use localHost or serverHost instead")
    override val host: String
        get() = hostHeaderValue?.substringBefore(":")
            ?: local.host
    override val localHost: String
        get() = local.localHost
    override val serverHost: String
        get() = local.serverHost
    override val localAddress: String
        get() = local.localAddress

    override val uri: String
        get() = local.uri

    override val method: HttpMethod
        get() = local.method

    override val remoteHost: String
        get() = local.remoteHost
    override val remotePort: Int
        get() = local.remotePort
    override val remoteAddress: String
        get() = local.remoteAddress

    override fun toString(): String =
        "OriginConnectionPoint(uri=$uri, method=$method, version=$version, localAddress=$localAddress, " +
            "localPort=$localPort, remoteAddress=$remoteAddress, remotePort=$remotePort)"
}

/**
 * Returns [MutableOriginConnectionPoint] associated with this call
 */

public val ApplicationCall.mutableOriginConnectionPoint: MutableOriginConnectionPoint
    get() = attributes.computeIfAbsent(MutableOriginConnectionPointKey) {
        MutableOriginConnectionPoint(OriginConnectionPoint(this))
    }
