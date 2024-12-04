/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlinx.io.*

/**
 * Copy all bytes to the [output].
 * Depending on actual input and output implementation it could be zero-copy or copy byte per byte.
 * All regular types such as [ByteReadPacket], [BytePacketBuilder], [Input] and [Output]
 * are always optimized so no bytes will be copied.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.core.copyTo)
 */
@Deprecated(
    "Use transferTo instead",
    ReplaceWith("output.transferTo(this)", "kotlinx.io.transferTo"),
    level = DeprecationLevel.ERROR
)
public fun Source.copyTo(output: Sink): Long = transferTo(output)
