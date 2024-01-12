/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import java.io.*
import java.util.*
import java.util.zip.*
import kotlin.test.*

internal suspend fun assertFailsSuspend(block: suspend () -> Unit): Throwable {
    var exception: Throwable? = null
    try {
        block()
    } catch (cause: Throwable) {
        exception = cause
    }

    assertNotNull(exception)
    return exception
}

internal fun InputStream.crcWithSize(): Pair<Long, Long> {
    val checksum = CRC32()
    val bytes = ByteArray(8192)
    var count = 0L

    do {
        val rc = read(bytes)
        if (rc == -1) {
            break
        }
        count += rc
        checksum.update(bytes, 0, rc)
    } while (true)

    return checksum.value to count
}

internal fun String.urlPath() = replace("\\", "/")

internal class ExpectedException(message: String) : RuntimeException(message)

internal fun loadTestFile(): File = listOf(
    File("jvm/src"),
    File("jvm/test"),
    File("ktor-server/ktor-server-core/jvm/src")
).filter { it.exists() }
    .flatMap { it.walkBottomUp().asIterable() }
    .first { it.extension == "kt" }

/**
 * Parse headers and return content length
 */
internal fun BufferedReader.parseHeadersAndGetContentLength(): Int {
    var contentLength = -1

    do {
        val line = readLine()
        if (line.isNullOrEmpty()) {
            break
        }

        when (line.split(" ", ":")[0].lowercase(Locale.getDefault())) {
            "content-length" -> contentLength = line.drop(16).trim().toInt()
            "transfer-encoding" -> error("We don't support chunked for 400 in this test")
        }
    } while (true)
    return contentLength
}

/**
 * Skip exactly [contentLength] bytes assuming UTF-8 character encoding
 */
internal fun BufferedReader.skipHttpResponseContent(contentLength: Int) {
    var current = 0
    while (current < contentLength) {
        val ch = read()
        assertNotEquals(
            -1,
            ch,
            "Server promised $contentLength bytes but we only got $current bytes",
        )
        when (ch.toChar()) {
            in '\u0000'..'\u007f' -> current++
            in '\u0080'..'\u07ff' -> current += 2
            in '\u0800'..'\uffff' -> current += 3
            else -> current += 4
        }
    }
}

internal inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    var failed = false
    try {
        block()
    } catch (cause: Throwable) {
        failed = true
        assertTrue(cause is T)
    }

    assertTrue(failed)
}
