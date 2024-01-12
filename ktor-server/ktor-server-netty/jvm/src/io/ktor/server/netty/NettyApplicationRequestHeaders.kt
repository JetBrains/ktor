/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.http.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders

public class NettyApplicationRequestHeaders(request: HttpRequest) : Headers {
    private val headers: HttpHeaders = request.headers()
    override fun get(name: String): String? = headers.get(name)
    override fun contains(name: String): Boolean = headers.contains(name)
    override fun contains(name: String, value: String): Boolean = headers.contains(name, value, true)
    override fun getAll(name: String): List<String>? = headers.getAll(name).takeIf { it.isNotEmpty() }
    override fun forEach(body: (String, List<String>) -> Unit) {
        val names = headers.names()
        names.forEach { body(it, headers.getAll(it)) }
    }

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        val names = headers.names()
        return names.mapTo(LinkedHashSet(names.size)) {
            object : Map.Entry<String, List<String>> {
                override val key: String get() = it
                override val value: List<String> get() = headers.getAll(it)
            }
        }
    }

    override fun isEmpty(): Boolean = headers.isEmpty
    override val caseInsensitiveName: Boolean get() = true
    override fun names(): Set<String> = headers.names()
}
