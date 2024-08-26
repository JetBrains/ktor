/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.util.*

internal fun getAnyLocalAddress(): NativeSocketAddress = InetSocketAddress("0.0.0.0", 0).address
