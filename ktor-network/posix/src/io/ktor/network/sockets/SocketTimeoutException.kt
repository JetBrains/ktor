/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.utils.io.errors.*
import kotlinx.io.IOException

@Suppress("EXPECT_WITHOUT_ACTUAL")
public actual class SocketTimeoutException(
    message: String,
    cause: Throwable?
) : IOException(message, cause) {
    public actual constructor(message: String) : this(message, null)
}
