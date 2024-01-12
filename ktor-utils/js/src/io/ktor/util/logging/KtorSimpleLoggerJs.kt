/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.util.*

@Suppress("FunctionName")
public actual fun KtorSimpleLogger(name: String): Logger = object : Logger {

    override val level: LogLevel = when (PlatformUtils.IS_NODE) {
        true -> runCatching { js("process.env.KTOR_LOG_LEVEL") as String? }
            .getOrNull()
            ?.let { rawLevel: String -> LogLevel.entries.firstOrNull { it.name == rawLevel } }
            ?: LogLevel.INFO

        false -> LogLevel.TRACE
    }

    override fun error(message: String) {
        if (level > LogLevel.ERROR) return
        console.error(message)
    }

    override fun error(message: String, cause: Throwable) {
        if (level > LogLevel.ERROR) return
        console.error("$message, cause: $cause")
    }

    override fun warn(message: String) {
        if (level > LogLevel.WARN) return
        console.warn(message)
    }

    override fun warn(message: String, cause: Throwable) {
        if (level > LogLevel.WARN) return
        console.warn("$message, cause: $cause")
    }

    override fun info(message: String) {
        if (level > LogLevel.INFO) return
        console.info(message)
    }

    override fun info(message: String, cause: Throwable) {
        if (level > LogLevel.INFO) return
        console.info("$message, cause: $cause")
    }

    override fun debug(message: String) {
        if (level > LogLevel.DEBUG) return
        console.debug("DEBUG: $message")
    }

    override fun debug(message: String, cause: Throwable) {
        if (level > LogLevel.DEBUG) return
        console.debug("DEBUG: $message, cause: $cause")
    }

    override fun trace(message: String) {
        if (level > LogLevel.TRACE) return
        console.debug("TRACE: $message")
    }

    override fun trace(message: String, cause: Throwable) {
        if (level > LogLevel.TRACE) return
        console.debug("TRACE: $message, cause: $cause")
    }
}

// kotlin Console class doesn't expose `debug` method
private external interface Console {
    fun error(vararg o: Any?)
    fun info(vararg o: Any?)
    fun log(vararg o: Any?)
    fun warn(vararg o: Any?)
    fun debug(vararg o: Any?)
}

private external val console: Console
