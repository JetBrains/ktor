/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ktlint-disable filename
package io.ktor.utils.io.core

import kotlin.experimental.*

public actual enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN;

    public actual companion object {
        @OptIn(ExperimentalNativeApi::class)
        private val native: ByteOrder = if (Platform.isLittleEndian) LITTLE_ENDIAN else BIG_ENDIAN

        public actual fun nativeOrder(): ByteOrder = native
    }
}
