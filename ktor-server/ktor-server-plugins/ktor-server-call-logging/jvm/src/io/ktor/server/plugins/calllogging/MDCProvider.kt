/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.calllogging

import io.ktor.server.application.*
import io.ktor.server.logging.*
import io.ktor.util.*

internal fun PluginBuilder<CallLoggingConfig>.setupMDCProvider() {
    application.pluginRegistry.put(KtorMDCProvider.key, KtorMDCProvider(pluginConfig.mdcEntries))
}

internal class KtorMDCProvider(private val entries: List<MDCEntry>) : MDCProvider {
    override suspend fun withMDCBlock(call: ApplicationCall, block: suspend () -> Unit) {
        withMDC(entries, call, block)
    }

    companion object {
        val key = AttributeKey<KtorMDCProvider>("KtorMDCProvider")
    }
}
