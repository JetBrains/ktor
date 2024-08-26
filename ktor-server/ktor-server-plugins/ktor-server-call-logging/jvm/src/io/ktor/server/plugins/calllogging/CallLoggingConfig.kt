/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.calllogging

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import org.fusesource.jansi.*
import org.slf4j.*
import org.slf4j.event.*

/**
 * A configuration for the [CallLogging] plugin.
 */
@KtorDsl
public class CallLoggingConfig {
    internal var clock: () -> Long = { getTimeMillis() }
    internal val filters = mutableListOf<(ApplicationCall) -> Boolean>()
    internal val mdcEntries = mutableListOf<MDCEntry>()
    internal var formatCall: (ApplicationCall) -> String = ::defaultFormat
    internal var isColorsEnabled: Boolean = true
    internal var ignoreStaticContent: Boolean = false

    /**
     * Specifies a logging level for the [CallLogging] plugin.
     * The default level is [Level.INFO].
     */
    public var level: Level = Level.INFO

    /**
     * Specifies a [Logger] used to log requests.
     * By default, uses [ApplicationEnvironment.log].
     */
    public var logger: Logger? = null

    /**
     * Allows you to add conditions for filtering requests.
     * In the example below, only requests made to `/api/v1` get into a log:
     * ```kotlin
     * filter { call ->
     *     call.request.path().startsWith("/api/v1")
     * }
     * ```
     *
     * @see [CallLogging]
     */
    public fun filter(predicate: (ApplicationCall) -> Boolean) {
        filters.add(predicate)
    }

    /**
     * Puts a diagnostic context value to [MDC] with the specified [name] and computed using the [provider] function.
     * A value is available in MDC only during [ApplicationCall] lifetime and is removed after a call processing.
     *
     * @see [CallLogging]
     */
    public fun mdc(name: String, provider: (ApplicationCall) -> String?) {
        mdcEntries.add(MDCEntry(name, provider))
    }

    /**
     * Allows you to configure a call log message.
     *
     * @see [CallLogging]
     */
    public fun format(formatter: (ApplicationCall) -> String) {
        formatCall = formatter
    }

    /**
     * Allows you to configure a clock that will be used to measure call processing time.
     *
     * @see [CallLogging]
     */
    public fun clock(clock: () -> Long) {
        this.clock = clock
    }

    /**
     * Disables colors in a log message when a default formatter is used.
     * */
    public fun disableDefaultColors() {
        isColorsEnabled = false
    }

    /**
     * Disables logging for static content files.
     * */
    public fun disableForStaticContent() {
        ignoreStaticContent = true
    }

    private fun defaultFormat(call: ApplicationCall): String =
        when (val status = call.response.status() ?: "Unhandled") {
            HttpStatusCode.Found -> "${colored(status as HttpStatusCode)}: " +
                "${call.request.toLogStringWithColors()} -> ${call.response.headers[HttpHeaders.Location]}"

            "Unhandled" -> "${colored(status, Ansi.Color.RED)}: ${call.request.toLogStringWithColors()}"
            else -> "${colored(status as HttpStatusCode)}: ${call.request.toLogStringWithColors()}"
        }

    internal fun ApplicationRequest.toLogStringWithColors(): String =
        "${colored(httpMethod.value, Ansi.Color.CYAN)} - ${path()} in ${call.processingTimeMillis(clock)}ms"

    private fun colored(status: HttpStatusCode): String {
        try {
            if (isColorsEnabled && !AnsiConsole.isInstalled()) {
                AnsiConsole.systemInstall()
            }
        } catch (cause: Throwable) {
            isColorsEnabled = false // ignore colors if console was not installed
        }

        return when (status) {
            HttpStatusCode.Found, HttpStatusCode.OK, HttpStatusCode.Accepted, HttpStatusCode.Created -> colored(
                status,
                Ansi.Color.GREEN
            )

            HttpStatusCode.Continue, HttpStatusCode.Processing, HttpStatusCode.PartialContent,
            HttpStatusCode.NotModified, HttpStatusCode.UseProxy, HttpStatusCode.UpgradeRequired,
            HttpStatusCode.NoContent -> colored(
                status,
                Ansi.Color.YELLOW
            )

            else -> colored(status, Ansi.Color.RED)
        }
    }

    private fun colored(value: Any, color: Ansi.Color): String =
        if (isColorsEnabled) {
            Ansi.ansi().fg(color).a(value).reset().toString()
        } else value.toString() // ignore color
}
