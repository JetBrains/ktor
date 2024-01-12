/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.cinterop.*
import platform.linux.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun inetNtopBridge(
    type: Int,
    address: CPointer<*>,
    addressOf: CPointer<*>,
    size: Int
) {
    @Suppress("UNCHECKED_CAST")
    inet_ntop(type, address, addressOf as CPointer<ByteVarOf<Byte>>, size.convert())
}
