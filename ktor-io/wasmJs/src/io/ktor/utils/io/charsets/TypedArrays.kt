/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.charsets

import org.khronos.webgl.*

private fun toJsArrayImpl(vararg x: Byte): Int8Array = js("new Int8Array(x)")

public fun ByteArray.toJsArray(): Int8Array = toJsArrayImpl(*this)

public fun Int8Array.toByteArray(): ByteArray =
    ByteArray(this.length) { this[it] }
