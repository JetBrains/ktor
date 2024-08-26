/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import jakarta.servlet.http.*

public abstract class ServletApplicationRequest(
    call: PipelineCall,
    public val servletRequest: HttpServletRequest
) : BaseApplicationRequest(call) {

    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters: Parameters by lazy { encodeParameters(rawQueryParameters).toQueryParameters() }

    override val rawQueryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        val uri = servletRequest.queryString ?: return@lazy Parameters.Empty
        parseQueryString(uri, decode = false)
    }

    override val engineHeaders: Headers = ServletApplicationRequestHeaders(servletRequest)

    @Suppress("LeakingThis") // this is safe because we don't access any content in the request
    override val cookies: RequestCookies = ServletApplicationRequestCookies(servletRequest, this)
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
