/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.defaultheaders

import io.ktor.server.application.*
import kotlinx.cinterop.*
import utils.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun <T : Any> readKtorVersion(plugin: RouteScopedPluginBuilder<T>): String {
    return read_ktor_version()?.toKString() ?: "debug"
}
