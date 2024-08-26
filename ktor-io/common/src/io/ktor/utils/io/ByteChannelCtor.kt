/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.Buffer

/**
 * Creates a channel for reading from the specified byte array. Please note that it could use [content] directly
 * or copy its bytes depending on the platform
 */
public fun ByteReadChannel(content: ByteArray, offset: Int = 0, length: Int = content.size): ByteReadChannel {
    val source = Buffer().also {
        it.write(content, startIndex = offset, endIndex = offset + length)
    }

    return ByteReadChannel(source)
}

public fun ByteReadChannel(text: String, charset: Charset = Charsets.UTF_8): ByteReadChannel =
    ByteReadChannel(text.toByteArray(charset))

public fun ByteReadChannel(source: Source): ByteReadChannel = SourceByteReadChannel(source)
