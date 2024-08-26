/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun setEnvironmentProperty(key: String, value: String): Unit = memScoped {
    SetEnvironmentVariable!!(key.wcstr.ptr, value.wcstr.ptr)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun clearEnvironmentProperty(key: String): Unit = memScoped {
    SetEnvironmentVariable!!(key.wcstr.ptr, null)
}
