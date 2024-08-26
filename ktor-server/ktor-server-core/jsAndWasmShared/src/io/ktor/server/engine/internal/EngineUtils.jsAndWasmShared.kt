/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

internal actual fun escapeHostname(value: String): String {
    val os = runCatching { platform() }.getOrNull() ?: return value

    // https://nodejs.org/api/process.html#processplatform
    if (os != "win32") return value
    if (value != "0.0.0.0") return value

    return "127.0.0.1"
}

internal fun platform(): String = js("process.platform")
