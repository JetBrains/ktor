/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.calllogging

import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.slf4j.*
import org.slf4j.*

internal class MDCEntry(val name: String, val provider: (ApplicationCall) -> String?)

internal suspend inline fun withMDC(
    mdcEntries: List<MDCEntry>,
    call: ApplicationCall,
    crossinline block: suspend () -> Unit
) {
    withContext(MDCContext(mdcEntries.setup(call))) {
        try {
            block()
        } finally {
            mdcEntries.cleanup()
        }
    }
}

internal fun List<MDCEntry>.setup(call: ApplicationCall): Map<String, String> {
    val result = MDC.getCopyOfContextMap() ?: mutableMapOf()

    val savedEntries = call.attributes.computeIfAbsent(MdcEntriesKey) { mutableMapOf() }
    for (entry in this) {
        val savedValue = savedEntries[entry.name]
        if (savedValue != null) {
            result[entry.name] = savedValue
            continue
        }
        val value = runCatching { entry.provider(call) }.getOrNull() ?: continue
        result[entry.name] = value
        savedEntries[entry.name] = value
    }

    return result
}

internal fun List<MDCEntry>.cleanup() {
    forEach {
        MDC.remove(it.name)
    }
}

private val MdcEntriesKey = AttributeKey<MutableMap<String, String>>("io.ktor.MDCEntries")
