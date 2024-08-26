/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.charsets

import org.khronos.webgl.*

private external class TextEncoder {
    fun encode(input: String): Int8Array
}

internal class JsTextEncoder private constructor(private val encoder: TextEncoder) {
    fun encode(input: String): ByteArray {
        val encodedArray = encoder.encode(input)
        return ByteArray(encodedArray.length) {
            encodedArray[it]
        }
    }

    companion object {
        fun create(): JsTextEncoder = JsTextEncoder(TextEncoder())
    }
}
