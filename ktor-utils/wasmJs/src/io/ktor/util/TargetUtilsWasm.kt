/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.asDeferred
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

private fun toJsArrayImpl(vararg x: Byte): Int8Array = js("new Int8Array(x)")

public actual fun ByteArray.toJsArray(): Int8Array = toJsArrayImpl(*this)

public actual fun Int8Array.toByteArray(): ByteArray =
    ByteArray(this.length) { this[it] }

@Suppress("DEPRECATION_ERROR")
internal actual suspend fun Promise<ArrayBuffer>.awaitBuffer(): ArrayBuffer =
    (this as Promise<JsAny?>).asDeferred<ArrayBuffer>().await()
