/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.response

import io.ktor.http.*

/**
 * Server's response headers.
 * @see [ApplicationResponse.headers]
 */
public abstract class ResponseHeaders {

    /**
     * A set of headers that is managed by an engine and should not be modified manually.
     */
    protected open val managedByEngineHeaders: Set<String> = emptySet()

    /**
     * Checks whether a [name] response header is set.
     */
    public operator fun contains(name: String): Boolean = get(name) != null

    /**
     * Gets a first response header with the specified [name] or returns `null`.
     */
    public open operator fun get(name: String): String? = getEngineHeaderValues(name).firstOrNull()

    /**
     * Gets values of a response header with the specified [name].
     */
    public fun values(name: String): List<String> = getEngineHeaderValues(name)

    /***
     * Builds a [Headers] instance from a response header values.
     */
    public fun allValues(): Headers = Headers.build {
        getEngineHeaderNames().toSet().forEach {
            appendAll(it, getEngineHeaderValues(it))
        }
    }

    /**
     * Appends a response header with the specified [name] and [value].
     * @param safeOnly prevents from setting unsafe headers; `true` by default
     */
    public fun append(name: String, value: String, safeOnly: Boolean = true) {
        if (managedByEngineHeaders.contains(name)) {
            return
        }
        if (safeOnly && HttpHeaders.isUnsafe(name)) {
            throw UnsafeHeaderException(name)
        }
        HttpHeaders.checkHeaderName(name)
        HttpHeaders.checkHeaderValue(value)
        engineAppendHeader(name, value)
    }

    /**
     * An engine's header appending implementation.
     */
    protected abstract fun engineAppendHeader(name: String, value: String)

    /**
     * An engine's response header names extractor.
     */
    protected abstract fun getEngineHeaderNames(): List<String>

    /**
     * An engine's response header values extractor.
     */
    protected abstract fun getEngineHeaderValues(name: String): List<String>
}

/**
 * Appends a response header with the specified [name] and [value] if this is no header with [name] yet.
 * @param safeOnly prevents from setting unsafe headers; `true` by default
 */
public fun ResponseHeaders.appendIfAbsent(name: String, value: String, safeOnly: Boolean = true) {
    if (contains(name)) return
    append(name, value, safeOnly)
}
