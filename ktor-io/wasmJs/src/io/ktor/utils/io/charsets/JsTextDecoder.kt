/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.js

import io.ktor.utils.io.charsets.*
import org.khronos.webgl.*

private external interface TextDecoder

private fun tryCreateTextDecoder(encoding: String, fatal: Boolean): TextDecoder =
    js("{ try { return new TextDecoder(encoding, { fatal: fatal }) } catch(e) { return null } }")

private fun decode(decoder: TextDecoder): String =
    js("{ try { return decoder.decode() } catch(e) { return null } }")

private fun decode(decoder: TextDecoder, buffer: Int8Array): String =
    js("{ try { return decoder.decode(buffer) } catch(e) { return null } }")

private fun decodeStream(decoder: TextDecoder, buffer: Int8Array): String =
    js("{ try { return decoder.decode(buffer, { stream: true }) } catch(e) { return null } }")

private inline fun decodeOrFail(body: () -> String?): String =
    body() ?: error("Buffer decode fail")

internal class JsTextDecoder private constructor(private val decoder: TextDecoder) : Decoder {
    override fun decode(): String =
        decodeOrFail { decode(decoder) }

    override fun decode(buffer: ByteArray): String =
        decodeOrFail { decode(decoder, buffer.toJsArray()) }

    override fun decodeStream(buffer: ByteArray): String =
        decodeOrFail { decodeStream(decoder, buffer.toJsArray()) }

    companion object {
        fun tryCreate(encoding: String, fatal: Boolean = true): JsTextDecoder =
            tryCreateTextDecoder(encoding, fatal).let(::JsTextDecoder)
    }
}
