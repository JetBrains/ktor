/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import kotlin.experimental.*

@OptIn(ExperimentalNativeApi::class)
internal actual fun escapeHostname(value: String): String {
    if (Platform.osFamily != OsFamily.WINDOWS) return value
    if (value != "0.0.0.0") return value

    return "127.0.0.1"
}
