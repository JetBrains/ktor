/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import javax.servlet.*

/**
 * Provides javax.servlet request attributes or fail it the underlying engine is not
 * servlet-backed.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.servlet.servletRequestAttributes)
 */
public val ApplicationRequest.servletRequestAttributes: Map<String, Any>
    get() = call.attributes[servletRequestAttributesKey]

/**
 * A key for call attribute containing java.servlet attributes.
 */
internal val servletRequestAttributesKey: AttributeKey<Map<String, Any>> = AttributeKey("ServletRequestAttributes")

public fun ApplicationCall.putServletAttributes(request: ServletRequest) {
    val servletAttributes = request.attributeNames?.asSequence()?.associateWith { attributeName ->
        request.getAttribute(attributeName)
    }?.filterValues { it != null } ?: emptyMap()

    attributes.put(servletRequestAttributesKey, servletAttributes)
}
