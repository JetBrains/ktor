/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.asDeferred
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

public actual fun ByteArray.toJsArray(): Int8Array = this.unsafeCast<Int8Array>()

public actual fun Int8Array.toByteArray(): ByteArray = this.unsafeCast<ByteArray>()

internal actual suspend fun Promise<ArrayBuffer>.awaitBuffer(): ArrayBuffer =
    asDeferred<ArrayBuffer>().await()
