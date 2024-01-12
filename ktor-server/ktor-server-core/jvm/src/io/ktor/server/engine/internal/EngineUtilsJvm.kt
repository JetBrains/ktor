/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

private val OS_NAME = System.getProperty("os.name", "")
    .lowercase()

internal actual fun escapeHostname(value: String): String {
    if (!OS_NAME.contains("windows")) return value
    if (value != "0.0.0.0") return value

    return "127.0.0.1"
}
