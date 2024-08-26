/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import org.khronos.webgl.*

internal external class TextDecoder(encoding: String, options: dynamic = definedExternally) {
    val encoding: String

    fun decode(): String
    fun decode(buffer: ArrayBuffer): String
    fun decode(buffer: ArrayBuffer, options: dynamic): String
    fun decode(buffer: ArrayBufferView): String
    fun decode(buffer: ArrayBufferView, options: dynamic): String
}

internal fun TextDecoder.toKtor(): Decoder = object : Decoder {
    override fun decode(): String = this@toKtor.decode()
    override fun decode(buffer: ArrayBufferView): String = this@toKtor.decode(buffer)
    override fun decode(buffer: ArrayBufferView, options: dynamic): String = this@toKtor.decode(buffer, options)
}

internal fun textDecoderOptions(fatal: Boolean = false): Any = Any().apply {
    with(this.asDynamic()) {
        this.fatal = fatal
    }
}
