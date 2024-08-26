/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.js.JsTextDecoder

internal fun Decoder(encoding: String, fatal: Boolean = true): Decoder =
    JsTextDecoder.tryCreate(encoding, fatal)
        ?: ISO8859TextDecoder.tryCreate(encoding, fatal)
        ?: error("$encoding is not supported.")

internal interface Decoder {
    fun decode(): String
    fun decode(buffer: ByteArray): String
    fun decodeStream(buffer: ByteArray): String
}
