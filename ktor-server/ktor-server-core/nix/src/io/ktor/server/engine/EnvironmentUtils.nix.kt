/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import platform.posix.*

internal actual fun setEnvironmentProperty(key: String, value: String) {
    setenv(key, value, 0)
}

internal actual fun clearEnvironmentProperty(key: String) {
    unsetenv(key)
}
