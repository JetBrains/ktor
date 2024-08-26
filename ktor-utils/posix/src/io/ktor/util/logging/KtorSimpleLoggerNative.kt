/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import kotlinx.cinterop.*
import platform.posix.*

private const val KTOR_LOG_LEVEL_KEY = "KTOR_LOG_LEVEL"

@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionName")
public actual fun KtorSimpleLogger(
    name: String
): Logger = object : Logger {

    override val level: LogLevel = getenv(KTOR_LOG_LEVEL_KEY)?.let { rawLevel ->
        val level = rawLevel.toKString()
        LogLevel.entries.firstOrNull { it.name == level }
    } ?: LogLevel.INFO

    private fun log(level: LogLevel, message: String) {
        if (level < this.level) return
        println("[${level.name}] ($name): $message")
    }

    private fun log(level: LogLevel, message: String, cause: Throwable) {
        if (level < this.level) return
        println("[${level.name}] ($name): $message. Cause: ${cause.stackTraceToString()}")
    }

    override fun error(message: String) {
        log(LogLevel.ERROR, message)
    }

    override fun error(message: String, cause: Throwable) {
        log(LogLevel.ERROR, message, cause)
    }

    override fun warn(message: String) {
        log(LogLevel.WARN, message)
    }

    override fun warn(message: String, cause: Throwable) {
        log(LogLevel.WARN, message, cause)
    }

    override fun info(message: String) {
        log(LogLevel.INFO, message)
    }

    override fun info(message: String, cause: Throwable) {
        log(LogLevel.INFO, message, cause)
    }

    override fun debug(message: String) {
        log(LogLevel.DEBUG, message)
    }

    override fun debug(message: String, cause: Throwable) {
        log(LogLevel.DEBUG, message, cause)
    }

    override fun trace(message: String) {
        log(LogLevel.TRACE, message)
    }

    override fun trace(message: String, cause: Throwable) {
        log(LogLevel.TRACE, message, cause)
    }
}
