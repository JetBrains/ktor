/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.methodoverride

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.utils.io.*

/**
 * A plugin that enables the capability to tunnel HTTP verbs inside the `X-HTTP-Method-Override` header.
 * This might be useful if your server API handles multiple HTTP verbs (`GET`, `PUT`, `POST`, `DELETE`, and so on),
 * but the client can only use a limited set of verbs (for example, `GET` and `POST`) due to specific limitations.
 * For instance, if the client sends a request with the `X-Http-Method-Override` header set to `DELETE`,
 * Ktor will process this request using the `delete` route handler.
 *
 * To learn more, see [XHttpMethodOverride](https://ktor.io/docs/x-http-method-override.html).
 */
public val XHttpMethodOverride: ApplicationPlugin<XHttpMethodOverrideConfig> = createApplicationPlugin(
    "XHttpMethodOverride",
    ::XHttpMethodOverrideConfig
) {
    on(CallSetup) { call ->
        call.request.header(pluginConfig.headerName)?.takeIf { it.isNotBlank() }?.let { methodOverride ->
            call.mutableOriginConnectionPoint.method = HttpMethod.parse(methodOverride)
        }
    }
}

/**
 * A configuration for the [XHttpMethodOverride] plugin.
 */
@KtorDsl
public class XHttpMethodOverrideConfig {
    /**
     * Specifies a name of the header used to override an HTTP method.
     */
    public var headerName: String = HttpHeaders.XHttpMethodOverride
}
