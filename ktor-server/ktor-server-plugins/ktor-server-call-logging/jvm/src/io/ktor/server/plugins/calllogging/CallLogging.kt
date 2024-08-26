/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.calllogging

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import org.slf4j.event.*

internal val CALL_START_TIME = AttributeKey<Long>("CallStartTime")

/**
 * Returns time in millis from the moment the call was received until now
 */
public fun ApplicationCall.processingTimeMillis(clock: () -> Long = { getTimeMillis() }): Long {
    val startTime = attributes[CALL_START_TIME]
    return clock() - startTime
}

/**
 * A plugin that allows you to log incoming client requests.
 * You can configure [CallLogging] in multiple ways: specify a logging level,
 * filter requests based on a specified condition, customize log messages, and so on.
 *
 * You can learn more from [Call logging](https://ktor.io/docs/call-logging.html).
 */
public val CallLogging: ApplicationPlugin<CallLoggingConfig> = createApplicationPlugin(
    "CallLogging",
    ::CallLoggingConfig
) {
    val log = pluginConfig.logger ?: application.log
    val filters = pluginConfig.filters
    val formatCall = pluginConfig.formatCall
    val clock = pluginConfig.clock
    val ignoreStaticContent = pluginConfig.ignoreStaticContent

    fun log(message: String) = when (pluginConfig.level) {
        Level.ERROR -> log.error(message)
        Level.WARN -> log.warn(message)
        Level.INFO -> log.info(message)
        Level.DEBUG -> log.debug(message)
        Level.TRACE -> log.trace(message)
    }

    fun logSuccess(call: ApplicationCall) {
        if ((ignoreStaticContent && call.isStaticContent()) || (filters.isNotEmpty() && filters.none { it(call) })) {
            return
        }
        log(formatCall(call))
    }

    setupMDCProvider()
    setupLogging(application.monitor, ::log)

    on(CallSetup) { call ->
        call.attributes.put(CALL_START_TIME, clock())
    }

    if (pluginConfig.mdcEntries.isEmpty()) {
        logCompletedCalls(::logSuccess)
        return@createApplicationPlugin
    }

    logCallsWithMDC(::logSuccess)
}

private fun PluginBuilder<CallLoggingConfig>.logCompletedCalls(logSuccess: (ApplicationCall) -> Unit) {
    on(ResponseSent) { call ->
        logSuccess(call)
    }
}

private fun PluginBuilder<CallLoggingConfig>.logCallsWithMDC(logSuccess: (ApplicationCall) -> Unit) {
    val entries = pluginConfig.mdcEntries

    on(MDCHook(ApplicationCallPipeline.Monitoring)) { call, proceed ->
        withMDC(entries, call, proceed)
    }

    on(MDCHook(ApplicationCallPipeline.Call)) { call, proceed ->
        withMDC(entries, call, proceed)
    }

    on(ResponseSent) { call ->
        withMDC(entries, call) {
            logSuccess(call)
        }
    }
}

private fun setupLogging(events: Events, log: (String) -> Unit) {
    val starting: (Application) -> Unit = { log("Application starting: $it") }
    val started: (Application) -> Unit = { log("Application started: $it") }
    val stopping: (Application) -> Unit = { log("Application stopping: $it") }
    var stopped: (Application) -> Unit = {}

    stopped = {
        log("Application stopped: $it")
        events.unsubscribe(ApplicationStarting, starting)
        events.unsubscribe(ApplicationStarted, started)
        events.unsubscribe(ApplicationStopping, stopping)
        events.unsubscribe(ApplicationStopped, stopped)
    }

    events.subscribe(ApplicationStarting, starting)
    events.subscribe(ApplicationStarted, started)
    events.subscribe(ApplicationStopping, stopping)
    events.subscribe(ApplicationStopped, stopped)
}
