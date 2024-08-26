/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import org.khronos.webgl.*

internal fun Decoder(encoding: String, fatal: Boolean = true): Decoder = try {
    TextDecoder(encoding, textDecoderOptions(fatal)).toKtor()
} catch (cause: Throwable) {
    TextDecoderFallback(encoding, fatal)
}

internal interface Decoder {
    fun decode(): String
    fun decode(buffer: ArrayBufferView): String
    fun decode(buffer: ArrayBufferView, options: dynamic): String
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Decoder.decodeStream(buffer: ArrayBufferView, stream: Boolean): String {
    decodeWrap {
        return decode(buffer, decodeOptions(stream))
    }
}

internal fun decodeOptions(stream: Boolean): dynamic = Any().apply {
    with(this.asDynamic()) {
        this.stream = stream
    }
}
