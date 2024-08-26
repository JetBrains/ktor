/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import kotlinx.io.*

internal actual class NetworkSocketTimeoutException actual constructor(message: String) : IOException(message)
