/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.io.*

/**
 * Builds an HTTP request or response
 */
public expect class RequestResponseBuilder() {
    /**
     * Append response status line
     */
    public fun responseLine(version: CharSequence, status: Int, statusText: CharSequence)

    /**
     * Append request line
     */
    public fun requestLine(method: HttpMethod, uri: CharSequence, version: CharSequence)

    /**
     * Append a line
     */
    public fun line(line: CharSequence)

    /**
     * Append raw bytes
     */
    public fun bytes(content: ByteArray, offset: Int = 0, length: Int = content.size)

    /**
     * Append header line
     */
    public fun headerLine(name: CharSequence, value: CharSequence)

    /**
     * Append an empty line (CR + LF in fact)
     */
    public fun emptyLine()

    /**
     * Build a packet of request/response
     */

    public fun build(): Source

    /**
     * Release all resources hold by the builder
     */
    public fun release()
}

private const val SP: Byte = 0x20
private const val CR: Byte = 0x0d
private const val LF: Byte = 0x0a
