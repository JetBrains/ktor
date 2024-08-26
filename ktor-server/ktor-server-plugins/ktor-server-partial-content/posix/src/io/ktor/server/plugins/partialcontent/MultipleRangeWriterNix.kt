/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.partialcontent

import io.ktor.utils.io.*
import kotlinx.coroutines.*

internal actual fun CoroutineScope.writeMultipleRangesImpl(
    channelProducer: (LongRange) -> ByteReadChannel,
    ranges: List<LongRange>,
    fullLength: Long?,
    boundary: String,
    contentType: String
): ByteReadChannel = throw NotImplementedError("Multiple ranges are not supported on native")

internal actual fun calculateMultipleRangesBodyLength(
    ranges: List<LongRange>,
    fullLength: Long?,
    boundary: String,
    contentType: String
): Long = throw NotImplementedError("Multiple ranges are not supported on native")
