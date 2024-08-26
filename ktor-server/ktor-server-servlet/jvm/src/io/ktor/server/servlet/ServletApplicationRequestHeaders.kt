/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet

import io.ktor.http.*
import java.util.*
import javax.servlet.http.*

public class ServletApplicationRequestHeaders(
    private val servletRequest: HttpServletRequest
) : Headers {
    override fun getAll(name: String): List<String>? {
        val headersEnumeration = servletRequest.getHeaders(name) ?: return null
        if (!headersEnumeration.hasMoreElements()) return null

        val first = headersEnumeration.nextElement()
        if (!headersEnumeration.hasMoreElements()) return Collections.singletonList(first)

        val result = ArrayList<String>(2)
        result.add(first)

        while (headersEnumeration.hasMoreElements()) {
            result.add(headersEnumeration.nextElement())
        }

        return result
    }

    override fun get(name: String): String? = servletRequest.getHeader(name)

    override fun contains(name: String): Boolean = servletRequest.getHeader(name) != null

    override fun forEach(body: (String, List<String>) -> Unit) {
        val namesEnumeration = servletRequest.headerNames ?: return
        while (namesEnumeration.hasMoreElements()) {
            val name = namesEnumeration.nextElement()
            val headersEnumeration = servletRequest.getHeaders(name) ?: continue
            val values = headersEnumeration.asSequence().toList()
            body(name, values)
        }
    }

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        val names = servletRequest.headerNames
        val set = LinkedHashSet<Map.Entry<String, List<String>>>()
        while (names.hasMoreElements()) {
            val name = names.nextElement()
            val entry = object : Map.Entry<String, List<String>> {
                override val key: String get() = name
                override val value: List<String> get() = getAll(name) ?: emptyList()
            }
            set.add(entry)
        }
        return set
    }

    override fun isEmpty(): Boolean = !servletRequest.headerNames.hasMoreElements()
    override val caseInsensitiveName: Boolean get() = true
    override fun names(): Set<String> = servletRequest.headerNames.asSequence().toSet()
}
